package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 집금에 성공한 요청 1건을 상환에 반영한다. 상환 가능 확인 → 납부 기록 → 충당 → 스케줄 판정·연체금 해소 판정
 * → 상태 판정 → 상태 변화 처리 → 상환 완료 표시를 한 트랜잭션으로 하고, 어느 단계에서 실패해도 모두 취소되어 집금 완료 상태로 남는다.
 * 집금 요청을 저장한 뒤 대출 잠금이 풀렸으므로, 다시 잠그고 지금 상태로 충당 대상을 구한다.
 * 납부 시각은 집금을 보낸 시각이고, 기준일은 그 날짜다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentReflectionService {

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
    private final CollectionRequestService collectionRequestService;

    @Transactional
    public void reflect(CollectionRequestResponse request) {
        Long loanAccountId = request.getLoanAccountId();
        BigDecimal amount = request.getAmount();
        LocalDate businessDate = request.getSendDate();

        RepaymentEligibilityResponse eligibility =
                repaymentEligibilityService.check(loanAccountId, amount, businessDate);
        Long repaymentId = insertRepayment(request);
        allocate(repaymentId, loanAccountId, amount, eligibility);

        for (RepaymentScheduleJudgmentTargetResponse schedule
                : repaymentScheduleMapper.findJudgmentTargets(loanAccountId)) {
            repaymentScheduleJudgmentService.judge(schedule, businessDate);
        }
        delinquencyResolutionService.resolve(loanAccountId);
        loanStatusJudgmentService.judge(loanAccountId, businessDate);
        loanStatusChangeService.process(loanAccountId, businessDate);
        collectionRequestService.markCompleted(request.getCollectionRequestId());
    }

    private Long insertRepayment(CollectionRequestResponse request) {
        RepaymentResponse repayment = RepaymentResponse.builder()
                .loanAccountId(request.getLoanAccountId())
                .repaymentChannelId(request.getRepaymentChannelId())
                // 중복 요청 방지는 아직 없다. NOT NULL·UNIQUE 칸이라 서버가 새로 만들어 채운다.
                .requestKey(UUID.randomUUID().toString())
                .paidAmount(request.getAmount())
                .paidAt(request.getSentAt())
                .transactionNo(request.getTransactionNo())
                .build();
        repaymentMapper.insertRepayment(repayment);
        return repayment.getRepaymentId();
    }

    private void allocate(Long repaymentId, Long loanAccountId, BigDecimal amount,
                          RepaymentEligibilityResponse eligibility) {
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
    }
}
