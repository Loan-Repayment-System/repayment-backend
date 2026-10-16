package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.exception.InstallmentDelinquencyChargeNotFoundException;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.repayment.dto.response.InstallmentRepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentTargetTypeMismatchException;
import com.repayment.domain.repayment.mapper.InstallmentRepaymentMapper;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
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
import java.util.Optional;

import static com.repayment.domain.repayment.service.RepaymentTargetType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("회차 충당")
class InstallmentAllocationServiceTest {

    private static final long REPAYMENT_ID = 77L;
    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long SECOND = 2L;
    private static final long THIRD = 3L;
    private static final long OCT_11_CHARGE = 101L;
    private static final long OCT_12_CHARGE = 102L;

    @Mock
    private InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private InstallmentRepaymentMapper installmentRepaymentMapper;

    @InjectMocks
    private InstallmentAllocationService installmentAllocationService;

    @Nested
    @DisplayName("21 연체: 2회 연체금 300원·300원, 2회 이자 50,000원, 3회 이자 45,000원, 2회 원금 500,000원, 3회 원금 505,000원일 때")
    class 연체_회차와_납부_기한_도래_회차 {

        @Test
        @DisplayName("600,000원을 내면 연체금 → 이자 → 2회 원금을 다 채우고 3회 원금은 4,400원만 채우며, 원금잔액에서 504,400원을 빼고 상세 2행을 남긴다")
        void 순서대로_채우고_마지막_대상은_남은_만큼만_채운다() {
            // given
            givenChargesBelongToSecond();

            // when
            installmentAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("600000"), overdueTargets());

            // then
            then(installmentDelinquencyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_11_CHARGE, new BigDecimal("300"));
            then(installmentDelinquencyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_12_CHARGE, new BigDecimal("300"));
            then(repaymentScheduleMapper).should().updateAddPaidInterest(SECOND, new BigDecimal("50000"));
            then(repaymentScheduleMapper).should().updateAddPaidInterest(THIRD, new BigDecimal("45000"));
            then(repaymentScheduleMapper).should().updateAddPaidPrincipal(SECOND, new BigDecimal("500000"));
            then(repaymentScheduleMapper).should().updateAddPaidPrincipal(THIRD, new BigDecimal("4400"));
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("504400"));
            assertThat(detailsOf(insertedDetails(2))).containsExactly(
                    "2:500000:50000:600",
                    "3:4400:45000:0");
        }

        @Test
        @DisplayName("1,100,600원을 내면 모든 대상을 다 채우고 원금잔액에서 1,005,000원을 뺀다")
        void 딱_맞게_내면_모두_채운다() {
            // given
            givenChargesBelongToSecond();

            // when
            installmentAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1100600"), overdueTargets());

            // then
            then(repaymentScheduleMapper).should().updateAddPaidPrincipal(THIRD, new BigDecimal("505000"));
            then(loanAccountMapper).should().updateSubtractLoanPrincipalBalance(LOAN_ACCOUNT_ID, new BigDecimal("1005000"));
            assertThat(detailsOf(insertedDetails(2))).containsExactly(
                    "2:500000:50000:600",
                    "3:505000:45000:0");
        }

        @Test
        @DisplayName("450원을 내면 10/11 연체금 300원과 10/12 연체금 150원만 채우고, 이자·원금과 원금잔액은 건드리지 않으며 상세 1행을 남긴다")
        void 연체금_중간에서_멈춘다() {
            // given
            givenChargesBelongToSecond();

            // when
            installmentAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("450"), overdueTargets());

            // then
            then(installmentDelinquencyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_11_CHARGE, new BigDecimal("300"));
            then(installmentDelinquencyChargeMapper).should().updateAddPaidDelinquencyCharge(OCT_12_CHARGE, new BigDecimal("150"));
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(detailsOf(insertedDetails(1))).containsExactly("2:0:0:450");
        }

        @Test
        @DisplayName("연체금 행의 회차를 찾지 못하면 회차 연체금을 찾을 수 없다고 거절한다")
        void 연체금의_회차가_없으면_거절한다() {
            // given
            given(installmentDelinquencyChargeMapper.findRepaymentScheduleId(OCT_11_CHARGE)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> installmentAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("600000"), overdueTargets()))
                    .isInstanceOf(InstallmentDelinquencyChargeNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("0 정상: 3회 이자 45,000원, 원금 505,000원일 때")
    class 납부_기한_도래_회차만 {

        @Test
        @DisplayName("45,000원을 내면 이자만 채우고 원금잔액은 건드리지 않으며 상세 1행(3회 이자 45,000원)을 남긴다")
        void 이자에서_딱_멈춘다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(1, INSTALLMENT_INTEREST, THIRD, "45000"),
                    target(2, INSTALLMENT_PRINCIPAL, THIRD, "505000"));

            // when
            installmentAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("45000"), targets);

            // then
            then(repaymentScheduleMapper).should().updateAddPaidInterest(THIRD, new BigDecimal("45000"));
            then(repaymentScheduleMapper).should(times(0)).updateAddPaidPrincipal(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(detailsOf(insertedDetails(1))).containsExactly("3:0:45000:0");
        }
    }

    @Nested
    @DisplayName("목록이 충당 순서와 다른 순서로 들어올 때")
    class 뒤섞인_목록 {

        @Test
        @DisplayName("원금(순서 2) → 이자(순서 1)로 받고 45,000원을 내면 충당 순서대로 3회 이자만 채우고 원금은 건드리지 않는다")
        void 충당_순서대로_채운다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(2, INSTALLMENT_PRINCIPAL, THIRD, "505000"),
                    target(1, INSTALLMENT_INTEREST, THIRD, "45000"));

            // when
            installmentAllocationService.allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("45000"), targets);

            // then
            then(repaymentScheduleMapper).should().updateAddPaidInterest(THIRD, new BigDecimal("45000"));
            then(repaymentScheduleMapper).should(times(0)).updateAddPaidPrincipal(anyLong(), any());
            then(loanAccountMapper).shouldHaveNoInteractions();
            assertThat(detailsOf(insertedDetails(1))).containsExactly("3:0:45000:0");
        }
    }

    @Nested
    @DisplayName("목록이 회차 충당과 맞지 않을 때")
    class 맞지_않는_목록 {

        @Test
        @DisplayName("기한이익상실의 옮긴 이자가 섞여 있으면 충당할 수 없는 대상이라고 거절하고 아무것도 바꾸지 않는다")
        void 회차_방식이_아닌_대상은_거절한다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(
                    target(1, INSTALLMENT_INTEREST, THIRD, "45000"),
                    target(2, ACCELERATION_CARRIED_INTEREST, 7L, "70000"));

            // when & then
            assertThatThrownBy(() -> installmentAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("1000"), targets))
                    .isInstanceOf(RepaymentTargetTypeMismatchException.class);
            then(installmentDelinquencyChargeMapper).shouldHaveNoInteractions();
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
            then(loanAccountMapper).shouldHaveNoInteractions();
            then(installmentRepaymentMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("목록 합이 5,000원인데 5,001원을 내면 충당하고 남은 금액이 있다고 거절한다")
        void 다_채우고_남으면_거절한다() {
            // given
            List<RepaymentTargetResponse> targets = List.of(target(1, INSTALLMENT_INTEREST, THIRD, "5000"));

            // when & then
            assertThatThrownBy(() -> installmentAllocationService.allocate(
                    REPAYMENT_ID, LOAN_ACCOUNT_ID, new BigDecimal("5001"), targets))
                    .isInstanceOfSatisfying(RepaymentAmountLeftOverException.class,
                            e -> assertThat(e.getLeftOverAmount()).isEqualByComparingTo("1"));
        }
    }

    private void givenChargesBelongToSecond() {
        given(installmentDelinquencyChargeMapper.findRepaymentScheduleId(OCT_11_CHARGE)).willReturn(Optional.of(SECOND));
        given(installmentDelinquencyChargeMapper.findRepaymentScheduleId(OCT_12_CHARGE)).willReturn(Optional.of(SECOND));
    }

    // 17이 만드는 충당 순서: 연체금(날짜순) → 이자(예정일순) → 원금(예정일순)
    private List<RepaymentTargetResponse> overdueTargets() {
        return List.of(
                target(1, INSTALLMENT_DELINQUENCY_CHARGE, OCT_11_CHARGE, "300"),
                target(2, INSTALLMENT_DELINQUENCY_CHARGE, OCT_12_CHARGE, "300"),
                target(3, INSTALLMENT_INTEREST, SECOND, "50000"),
                target(4, INSTALLMENT_INTEREST, THIRD, "45000"),
                target(5, INSTALLMENT_PRINCIPAL, SECOND, "500000"),
                target(6, INSTALLMENT_PRINCIPAL, THIRD, "505000"));
    }

    private RepaymentTargetResponse target(int order, RepaymentTargetType type, long targetId, String amount) {
        return new RepaymentTargetResponse(order, type, targetId, new BigDecimal(amount));
    }

    private List<InstallmentRepaymentResponse> insertedDetails(int count) {
        ArgumentCaptor<InstallmentRepaymentResponse> captor = ArgumentCaptor.forClass(InstallmentRepaymentResponse.class);
        then(installmentRepaymentMapper).should(times(count)).insertInstallmentRepayment(captor.capture());
        return captor.getAllValues();
    }

    // "회차:원금:이자:연체금". 모두 상환 77의 상세인지도 함께 확인한다.
    private List<String> detailsOf(List<InstallmentRepaymentResponse> details) {
        assertThat(details).allSatisfy(detail -> assertThat(detail.getRepaymentId()).isEqualTo(REPAYMENT_ID));
        return details.stream()
                .map(detail -> detail.getRepaymentScheduleId() + ":"
                        + detail.getPaidPrincipal().toPlainString() + ":"
                        + detail.getPaidInterest().toPlainString() + ":"
                        + detail.getPaidDelinquencyCharge().toPlainString())
                .toList();
    }
}
