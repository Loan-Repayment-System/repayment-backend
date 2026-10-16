package com.repayment.domain.repayment.service;

import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.acceleration.mapper.LoanAccelerationMapper;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
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
@DisplayName("기한이익상실 충당")
class AccelerationAllocationServiceTest {

    private static final long REPAYMENT_ID = 77L;
    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long ACCELERATION_ID = 5L;
    private static final long OCT_15_CHARGE = 201L;
    private static final long OCT_16_CHARGE = 202L;

    @Mock
    private LoanAccelerationMapper loanAccelerationMapper;

    @Mock
    private AccelerationDailyChargeMapper accelerationDailyChargeMapper;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private RepaymentAllocationMapper repaymentAllocationMapper;

    @InjectMocks
    private AccelerationAllocationService accelerationAllocationService;

    @Nested
    @DisplayName("옮긴 연체금 500원, 하루 연체금 400원·400원, 옮긴 이자 70,000원, 하루 약정이자 1,400원·1,400원, 옮긴 밀린 원금 1,500,000원, 나머지 원금 8,500,000원일 때")
    class 옮긴_금액과_하루_기록 {

        @Test
        @DisplayName("1,000원을 내면 옮긴 연체금 500원, 10/15 연체금 400원, 10/16 연체금 100원만 채우고, 이자·원금과 원금잔액은 건드리지 않으며 충당 상세 3행을 남긴다")
        void 연체금_중간에서_멈춘다() {
            // when
            accelerationAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1000"), accelerationTargets());

            // then
            then(loanAccelerationMapper).should().updateAddPaidCarriedDelinquencyCharge(ACCELERATION_ID, new BigDecimal("500"));
            then(accelerationDailyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_15_CHARGE, new BigDecimal("400"));
            then(accelerationDailyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_16_CHARGE, new BigDecimal("100"));
            then(loanAccelerationMapper).should(times(0)).updateAddPaidCarriedInterest(anyLong(), any());
            then(loanAccelerationMapper).should(times(0)).updateAddPaidCarriedOverduePrincipal(anyLong(), any());
            then(accelerationDailyChargeMapper).should(times(0)).updateAddPaidInterest(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(allocationsOf(insertedAllocations(3))).containsExactly(
                    "ACCELERATION_CARRIED_DELINQUENCY_CHARGE:5:500",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:201:400",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:202:100");
        }

        @Test
        @DisplayName("1,000,000원을 내면 연체금·이자를 다 채우고 옮긴 밀린 원금은 925,900원만 채우며, 원금잔액에서 925,900원을 빼고 충당 상세 7행을 남긴다")
        void 옮긴_밀린_원금도_원금잔액에서_뺀다() {
            // when
            accelerationAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1000000"), accelerationTargets());

            // then
            then(loanAccelerationMapper).should().updateAddPaidCarriedInterest(ACCELERATION_ID, new BigDecimal("70000"));
            then(accelerationDailyChargeMapper).should().updateAddPaidInterest(OCT_15_CHARGE, new BigDecimal("1400"));
            then(accelerationDailyChargeMapper).should().updateAddPaidInterest(OCT_16_CHARGE, new BigDecimal("1400"));
            then(loanAccelerationMapper).should().updateAddPaidCarriedOverduePrincipal(ACCELERATION_ID, new BigDecimal("925900"));
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("925900"));
            assertThat(allocationsOf(insertedAllocations(7))).containsExactly(
                    "ACCELERATION_CARRIED_DELINQUENCY_CHARGE:5:500",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:201:400",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:202:400",
                    "ACCELERATION_CARRIED_INTEREST:5:70000",
                    "ACCELERATION_DAILY_INTEREST:201:1400",
                    "ACCELERATION_DAILY_INTEREST:202:1400",
                    "ACCELERATION_CARRIED_OVERDUE_PRINCIPAL:5:925900");
        }

        @Test
        @DisplayName("2,000,000원을 내면 옮긴 밀린 원금까지 다 채우고 나머지 원금은 425,900원만 채우며, 원금잔액에서 두 원금 합 1,925,900원을 한 번 빼고 충당 상세 8행을 남긴다")
        void 나머지_원금은_원금잔액에서만_뺀다() {
            // when
            accelerationAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("2000000"), accelerationTargets());

            // then
            then(loanAccelerationMapper).should().updateAddPaidCarriedOverduePrincipal(ACCELERATION_ID, new BigDecimal("1500000"));
            then(loanAccountMapper).should(times(1)).updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("1925900"));
            assertThat(allocationsOf(insertedAllocations(8))).containsExactly(
                    "ACCELERATION_CARRIED_DELINQUENCY_CHARGE:5:500",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:201:400",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:202:400",
                    "ACCELERATION_CARRIED_INTEREST:5:70000",
                    "ACCELERATION_DAILY_INTEREST:201:1400",
                    "ACCELERATION_DAILY_INTEREST:202:1400",
                    "ACCELERATION_CARRIED_OVERDUE_PRINCIPAL:5:1500000",
                    "ACCELERATION_REMAINING_PRINCIPAL:10:425900");
        }

        @Test
        @DisplayName("10,074,100원을 내면 모든 대상을 다 채우고 원금잔액에서 10,000,000원을 빼며 충당 상세 8행을 남긴다")
        void 딱_맞게_내면_모두_채운다() {
            // when
            accelerationAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("10074100"), accelerationTargets());

            // then
            then(loanAccelerationMapper).should().updateAddPaidCarriedOverduePrincipal(ACCELERATION_ID, new BigDecimal("1500000"));
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("10000000"));
            assertThat(allocationsOf(insertedAllocations(8))).containsExactly(
                    "ACCELERATION_CARRIED_DELINQUENCY_CHARGE:5:500",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:201:400",
                    "ACCELERATION_DAILY_DELINQUENCY_CHARGE:202:400",
                    "ACCELERATION_CARRIED_INTEREST:5:70000",
                    "ACCELERATION_DAILY_INTEREST:201:1400",
                    "ACCELERATION_DAILY_INTEREST:202:1400",
                    "ACCELERATION_CARRIED_OVERDUE_PRINCIPAL:5:1500000",
                    "ACCELERATION_REMAINING_PRINCIPAL:10:8500000");
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
                    target(2, ACCELERATION_CARRIED_INTEREST, ACCELERATION_ID, "70000"),
                    target(1, ACCELERATION_CARRIED_DELINQUENCY_CHARGE, ACCELERATION_ID, "500"));

            // when
            accelerationAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("500"), targets);

            // then
            then(loanAccelerationMapper).should().updateAddPaidCarriedDelinquencyCharge(ACCELERATION_ID, new BigDecimal("500"));
            then(loanAccelerationMapper).should(times(0)).updateAddPaidCarriedInterest(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(allocationsOf(insertedAllocations(1))).containsExactly("ACCELERATION_CARRIED_DELINQUENCY_CHARGE:5:500");
        }
    }

    @Nested
    @DisplayName("목록이 기한이익상실 충당과 맞지 않을 때")
    class 맞지_않는_목록 {

        @Test
        @DisplayName("회차 이자가 섞여 있으면 충당할 수 없는 대상이라고 거절하고 아무것도 바꾸지 않는다")
        void 기한이익상실_방식이_아닌_대상은_거절한다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(1, ACCELERATION_CARRIED_INTEREST, ACCELERATION_ID, "70000"),
                    target(2, INSTALLMENT_INTEREST, 3L, "45000"));

            // when & then
            assertThatThrownBy(() -> accelerationAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1000"), targets))
                    .isInstanceOf(RepaymentTargetTypeMismatchException.class);
            then(loanAccelerationMapper).shouldHaveNoInteractions();
            then(accelerationDailyChargeMapper).shouldHaveNoInteractions();
            then(loanAccountMapper).shouldHaveNoInteractions();
            then(repaymentAllocationMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("목록 합이 5,000원인데 5,001원을 내면 충당하고 남은 금액이 있다고 거절한다")
        void 다_채우고_남으면_거절한다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(target(1, ACCELERATION_CARRIED_INTEREST, ACCELERATION_ID, "5000"));

            // when & then
            assertThatThrownBy(() -> accelerationAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("5001"), targets))
                    .isInstanceOfSatisfying(RepaymentAmountLeftOverException.class,
                            e -> assertThat(e.getLeftOverAmount()).isEqualByComparingTo("1"));
        }
    }

    // 17이 만드는 충당 순서: 연체금(옮긴 것 → 날짜순) → 이자(옮긴 것 → 날짜순) → 원금(옮긴 밀린 원금 → 나머지 원금)
    private List<RepaymentTargetResponse> accelerationTargets() {
        return List.of(
                target(1, ACCELERATION_CARRIED_DELINQUENCY_CHARGE, ACCELERATION_ID, "500"),
                target(2, ACCELERATION_DAILY_DELINQUENCY_CHARGE, OCT_15_CHARGE, "400"),
                target(3, ACCELERATION_DAILY_DELINQUENCY_CHARGE, OCT_16_CHARGE, "400"),
                target(4, ACCELERATION_CARRIED_INTEREST, ACCELERATION_ID, "70000"),
                target(5, ACCELERATION_DAILY_INTEREST, OCT_15_CHARGE, "1400"),
                target(6, ACCELERATION_DAILY_INTEREST, OCT_16_CHARGE, "1400"),
                target(7, ACCELERATION_CARRIED_OVERDUE_PRINCIPAL, ACCELERATION_ID, "1500000"),
                target(8, ACCELERATION_REMAINING_PRINCIPAL, LOAN_ACCOUNT_ID, "8500000"));
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
