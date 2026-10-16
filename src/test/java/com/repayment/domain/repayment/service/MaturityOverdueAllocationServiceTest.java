package com.repayment.domain.repayment.service;

import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.maturity.mapper.LoanMaturityOverdueMapper;
import com.repayment.domain.maturity.mapper.MaturityOverdueDailyChargeMapper;
import com.repayment.domain.repayment.dto.response.RepaymentAllocationResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentTargetTypeMismatchException;
import com.repayment.domain.repayment.mapper.RepaymentAllocationMapper;
import com.repayment.global.code.RepaymentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.repayment.global.code.RepaymentTargetType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("만기 경과 충당")
class MaturityOverdueAllocationServiceTest {

    private static final long REPAYMENT_ID = 77L;
    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long MATURITY_OVERDUE_ID = 6L;
    private static final long OCT_15_CHARGE = 301L;
    private static final long OCT_16_CHARGE = 302L;

    @Mock
    private LoanMaturityOverdueMapper loanMaturityOverdueMapper;

    @Mock
    private MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private RepaymentAllocationMapper repaymentAllocationMapper;

    @InjectMocks
    private MaturityOverdueAllocationService maturityOverdueAllocationService;

    @Nested
    @DisplayName("옮긴 연체금 500원, 하루 연체금 2,400원·2,400원, 옮긴 이자 70,000원, 원금잔액 10,000,000원일 때")
    class 옮긴_금액과_하루_기록 {

        @Test
        @DisplayName("3,000원을 내면 옮긴 연체금 500원, 10/15 연체금 2,400원, 10/16 연체금 100원만 채우고, 이자와 원금잔액은 건드리지 않으며 충당 상세 3행을 남긴다")
        void 연체금_중간에서_멈춘다() {
            // when
            maturityOverdueAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("3000"), maturityOverdueTargets());

            // then
            then(loanMaturityOverdueMapper).should().updateAddPaidCarriedDelinquencyCharge(MATURITY_OVERDUE_ID, new BigDecimal("500"));
            then(maturityOverdueDailyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_15_CHARGE, new BigDecimal("2400"));
            then(maturityOverdueDailyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_16_CHARGE, new BigDecimal("100"));
            then(loanMaturityOverdueMapper).should(times(0)).updateAddPaidCarriedInterest(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(allocationsOf(insertedAllocations(3))).containsExactly(
                    "MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE:6:500",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:301:2400",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:302:100");
        }

        @Test
        @DisplayName("100,000원을 내면 연체금·이자를 다 채우고 원금잔액은 24,700원만 채우며, 원금잔액에서 24,700원을 빼고 충당 상세 5행을 남긴다")
        void 원금은_원금잔액에서만_뺀다() {
            // when
            maturityOverdueAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("100000"), maturityOverdueTargets());

            // then
            then(loanMaturityOverdueMapper).should().updateAddPaidCarriedInterest(MATURITY_OVERDUE_ID, new BigDecimal("70000"));
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("24700"));
            assertThat(allocationsOf(insertedAllocations(5))).containsExactly(
                    "MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE:6:500",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:301:2400",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:302:2400",
                    "MATURITY_OVERDUE_CARRIED_INTEREST:6:70000",
                    "MATURITY_OVERDUE_PRINCIPAL:10:24700");
        }

        @Test
        @DisplayName("10,075,300원을 내면 모든 대상을 다 채우고 원금잔액에서 10,000,000원을 빼며 충당 상세 5행을 남긴다")
        void 딱_맞게_내면_모두_채운다() {
            // when
            maturityOverdueAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("10075300"), maturityOverdueTargets());

            // then
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("10000000"));
            assertThat(allocationsOf(insertedAllocations(5))).containsExactly(
                    "MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE:6:500",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:301:2400",
                    "MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:302:2400",
                    "MATURITY_OVERDUE_CARRIED_INTEREST:6:70000",
                    "MATURITY_OVERDUE_PRINCIPAL:10:10000000");
        }
    }

    @Nested
    @DisplayName("목록이 충당 순서와 다른 순서로 들어올 때")
    class 뒤섞인_목록 {

        @Test
        @DisplayName("옮긴 이자(순서 2) → 옮긴 연체금(순서 1)으로 받고 500원을 내면 충당 순서대로 옮긴 연체금만 채우고 이자는 건드리지 않는다")
        void 충당_순서대로_채운다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(2, MATURITY_OVERDUE_CARRIED_INTEREST, MATURITY_OVERDUE_ID, "70000"),
                    target(1, MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE, MATURITY_OVERDUE_ID, "500"));

            // when
            maturityOverdueAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("500"), targets);

            // then
            then(loanMaturityOverdueMapper).should().updateAddPaidCarriedDelinquencyCharge(MATURITY_OVERDUE_ID, new BigDecimal("500"));
            then(loanMaturityOverdueMapper).should(times(0)).updateAddPaidCarriedInterest(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(allocationsOf(insertedAllocations(1))).containsExactly("MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE:6:500");
        }
    }

    @Nested
    @DisplayName("목록이 만기 경과 충당과 맞지 않을 때")
    class 맞지_않는_목록 {

        @Test
        @DisplayName("기한이익상실의 옮긴 이자가 섞여 있으면 충당할 수 없는 대상이라고 거절하고 아무것도 바꾸지 않는다")
        void 만기_경과_방식이_아닌_대상은_거절한다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(1, MATURITY_OVERDUE_CARRIED_INTEREST, MATURITY_OVERDUE_ID, "70000"),
                    target(2, ACCELERATION_CARRIED_INTEREST, 5L, "70000"));

            // when & then
            assertThatThrownBy(() -> maturityOverdueAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1000"), targets))
                    .isInstanceOf(RepaymentTargetTypeMismatchException.class);
            then(loanMaturityOverdueMapper).shouldHaveNoInteractions();
            then(maturityOverdueDailyChargeMapper).shouldHaveNoInteractions();
            then(loanAccountMapper).shouldHaveNoInteractions();
            then(repaymentAllocationMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("목록 합이 5,000원인데 5,001원을 내면 충당하고 남은 금액이 있다고 거절한다")
        void 다_채우고_남으면_거절한다() {
            // given
            List<RepaymentTargetResponse> targets =
                    List.of(target(1, MATURITY_OVERDUE_CARRIED_INTEREST, MATURITY_OVERDUE_ID, "5000"));

            // when & then
            assertThatThrownBy(() -> maturityOverdueAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("5001"), targets))
                    .isInstanceOfSatisfying(RepaymentAmountLeftOverException.class,
                            e -> assertThat(e.getLeftOverAmount()).isEqualByComparingTo("1"));
        }
    }

    // 17이 만드는 충당 순서: 연체금(옮긴 것 → 날짜순) → 옮긴 이자 → 원금잔액
    private List<RepaymentTargetResponse> maturityOverdueTargets() {
        return List.of(
                target(1, MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE, MATURITY_OVERDUE_ID, "500"),
                target(2, MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE, OCT_15_CHARGE, "2400"),
                target(3, MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE, OCT_16_CHARGE, "2400"),
                target(4, MATURITY_OVERDUE_CARRIED_INTEREST, MATURITY_OVERDUE_ID, "70000"),
                target(5, MATURITY_OVERDUE_PRINCIPAL, LOAN_ACCOUNT_ID, "10000000"));
    }

    private RepaymentTargetResponse target(int order, RepaymentTargetType type, long targetId, String amount) {
        return new RepaymentTargetResponse(order, type, targetId, new BigDecimal(amount));
    }

    private List<RepaymentAllocationResponse> insertedAllocations(int count) {
        ArgumentCaptor<RepaymentAllocationResponse> captor = ArgumentCaptor.forClass(RepaymentAllocationResponse.class);
        then(repaymentAllocationMapper).should(times(count)).insertAllocation(captor.capture());
        return captor.getAllValues();
    }

    // "종류:대상 id:충당 금액". 모두 상환 77의 충당 상세인지도 함께 확인한다.
    private List<String> allocationsOf(List<RepaymentAllocationResponse> allocations) {
        assertThat(allocations).allSatisfy(allocation -> assertThat(allocation.getRepaymentId()).isEqualTo(REPAYMENT_ID));
        return allocations.stream()
                .map(allocation -> allocation.getRepaymentTargetTypeId() + ":"
                        + allocation.getTargetId() + ":"
                        + allocation.getPaidAmount().toPlainString())
                .toList();
    }
}
