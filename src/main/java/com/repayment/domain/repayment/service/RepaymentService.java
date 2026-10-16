package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.CollectionAccountNotFoundException;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import com.repayment.domain.transfer.mapper.AccountMapper;
import com.repayment.domain.transfer.service.InternalTransferService;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.config.RepaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 상환 요청 1건을 처리한다. 채널(고객 상환·자동 상환)은 부르는 쪽이 정해 넘긴다. 상환 가능 확인 → 당행 이체 → 납부 기록 → 충당 → 스케줄 판정·연체금 해소 판정
 * → 상태 판정 → 상태 변화 처리를 한 트랜잭션으로 하고, 어느 단계에서 실패해도 이체까지 모두 취소된다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final RepaymentEligibilityService repaymentEligibilityService;
    private final AccountMapper accountMapper;
    private final InternalTransferService internalTransferService;
    private final RepaymentMapper repaymentMapper;
    private final InstallmentAllocationService installmentAllocationService;
    private final AccelerationAllocationService accelerationAllocationService;
    private final MaturityOverdueAllocationService maturityOverdueAllocationService;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;
    private final DelinquencyResolutionService delinquencyResolutionService;
    private final LoanStatusJudgmentService loanStatusJudgmentService;
    private final LoanStatusChangeService loanStatusChangeService;
    private final RepaymentProperties repaymentProperties;
    private final Clock clock;

    @Transactional
    public Long repay(Long loanAccountId, Long memberId, BigDecimal amount, String requestKey,
                      RepaymentChannel channel) {
        // 납부 시각과 판정 기준일이 자정을 사이에 두고 어긋나지 않게 한 번만 읽는다.
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate businessDate = now.toLocalDate();

        RepaymentEligibilityResponse eligibility =
                repaymentEligibilityService.check(loanAccountId, memberId, amount);

        String collectionAccountNo = repaymentProperties.collectionAccountNo();
        Long collectionAccountId = accountMapper.findAccountIdByAccountNo(collectionAccountNo)
                .orElseThrow(() -> new CollectionAccountNotFoundException(collectionAccountNo));
        String transactionNo = UUID.randomUUID().toString();
        internalTransferService.transfer(eligibility.getRepaymentAccountId(), collectionAccountId, amount,
                transactionNo);

        RepaymentResponse repayment = RepaymentResponse.builder()
                .loanAccountId(loanAccountId)
                .repaymentChannelId(channel)
                .requestKey(requestKey)
                .paidAmount(amount)
                .paidAt(now)
                .transactionNo(transactionNo)
                .build();
        repaymentMapper.insertRepayment(repayment);
        Long repaymentId = repayment.getRepaymentId();

        List<RepaymentTargetResponse> targets = eligibility.getRepayableAmount().getTargets();
        switch (eligibility.getLatestJudgment()) {
            case NORMAL, BULLET_INTEREST_OVERDUE, INSTALLMENT_OVERDUE ->
                    installmentAllocationService.allocate(repaymentId, loanAccountId, amount, targets);
            case BULLET_ACCELERATION, BULLET_ACCELERATION_CLAUSE_6,
                 INSTALLMENT_ACCELERATION, INSTALLMENT_ACCELERATION_CLAUSE_6 ->
                    accelerationAllocationService.allocate(repaymentId, loanAccountId, amount, targets);
            case MATURITY_OVERDUE ->
                    maturityOverdueAllocationService.allocate(repaymentId, loanAccountId, amount, targets);
            // 상환 가능 확인이 완제를 거절하므로 오지 않는다.
            case PAID_OFF -> throw new LoanAlreadyPaidOffException(loanAccountId);
        }

        for (RepaymentScheduleJudgmentTargetResponse schedule
                : repaymentScheduleMapper.findJudgmentTargets(loanAccountId)) {
            repaymentScheduleJudgmentService.judge(schedule, businessDate);
        }
        delinquencyResolutionService.resolve(loanAccountId);
        loanStatusJudgmentService.judge(loanAccountId, businessDate);
        loanStatusChangeService.process(loanAccountId, businessDate);
        return repaymentId;
    }
}
