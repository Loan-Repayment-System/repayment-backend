package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.dto.response.LoanAccountStatusResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.loan.exception.LoanStatusJudgmentNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 상환 요청 1건을 처리한다. 채널(고객 상환·자동 상환)은 부르는 쪽이 정해 넘긴다. 상환 가능 확인 → 납부 기록 → 충당 → 스케줄 판정·연체금 해소 판정
 * → 상태 판정 → 상태 변화 처리를 한 트랜잭션으로 하고, 어느 단계에서 실패해도 모두 취소된다.
 * 마지막에 상환을 모두 반영한 원금잔액·대출 상태·남은 갚을 금액을 읽어 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final RepaymentEligibilityService repaymentEligibilityService;
    private final RepaymentMapper repaymentMapper;
    private final InstallmentAllocationService installmentAllocationService;
    private final AccelerationAllocationService accelerationAllocationService;
    private final MaturityOverdueAllocationService maturityOverdueAllocationService;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;
    private final DelinquencyResolutionService delinquencyResolutionService;
    private final LoanStatusJudgmentService loanStatusJudgmentService;
    private final LoanStatusChangeService loanStatusChangeService;
    private final LoanAccountMapper loanAccountMapper;
    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final RepayableAmountService repayableAmountService;
    private final Clock clock;

    @Transactional
    public RepaymentResultResponse repay(Long loanAccountId, BigDecimal amount, RepaymentChannel channel) {
        // 납부 시각과 판정 기준일이 자정을 사이에 두고 어긋나지 않게 한 번만 읽는다.
        // DATETIME은 초 미만을 반올림해 저장하므로, 저장할 값과 돌려줄 값이 같도록 먼저 버린다.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDate businessDate = now.toLocalDate();

        RepaymentEligibilityResponse eligibility =
                repaymentEligibilityService.check(loanAccountId, amount, businessDate);

        // TODO(firmbanking): 상환 계좌에서 수납 계좌(repayment.collection-*)로 출금 이체 전문을 보내고,
        //  응답의 거래번호를 transactionNo로 쓴다. 외부 이체는 DB 롤백으로 되돌릴 수 없으므로
        //  호출 순서와 실패 처리를 함께 정한다.
        String transactionNo = UUID.randomUUID().toString();

        RepaymentResponse repayment = RepaymentResponse.builder()
                .loanAccountId(loanAccountId)
                .repaymentChannelId(channel)
                // 중복 요청 방지는 아직 없다. NOT NULL·UNIQUE 칸이라 서버가 새로 만들어 채운다.
                .requestKey(UUID.randomUUID().toString())
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
        return readResult(loanAccountId, amount, now, businessDate);
    }

    private RepaymentResultResponse readResult(Long loanAccountId, BigDecimal paidAmount, LocalDateTime paidAt,
                                               LocalDate businessDate) {
        LoanAccountStatusResponse loanAccount = loanAccountMapper.findLoanAccountStatus(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        // 상태 판정이 방금 판정을 남겼으므로 비어 있으면 데이터 오류다.
        LoanJudgmentStatus latestJudgment = loanStatusJudgmentMapper.findLatestJudgmentStatus(loanAccountId)
                .orElseThrow(() -> new LoanStatusJudgmentNotFoundException(loanAccountId, businessDate));
        BigDecimal remainingRepayableAmount =
                repayableAmountService.calculate(loanAccountId, latestJudgment).getTotalAmount();
        return RepaymentResultResponse.builder()
                .paidAmount(paidAmount)
                .paidAt(paidAt)
                .loanPrincipalBalance(loanAccount.getLoanPrincipalBalance())
                .loanStatus(loanAccount.getLoanStatusId())
                .remainingRepayableAmount(remainingRepayableAmount)
                .build();
    }
}
