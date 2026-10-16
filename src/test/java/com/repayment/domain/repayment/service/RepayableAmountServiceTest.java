package com.repayment.domain.repayment.service;

import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.UnpaidAccelerationResponse;
import com.repayment.domain.repayment.dto.response.UnpaidDailyChargeResponse;
import com.repayment.domain.repayment.dto.response.UnpaidInstallmentResponse;
import com.repayment.domain.repayment.dto.response.UnpaidMaturityOverdueResponse;
import com.repayment.domain.repayment.mapper.RepaymentTargetMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("갚을 금액 계산")
class RepayableAmountServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long ACCELERATION_ID = 7L;
    private static final long MATURITY_OVERDUE_ID = 8L;

    @Mock
    private RepaymentTargetMapper repaymentTargetMapper;

    @InjectMocks
    private RepayableAmountService repayableAmountService;

    @Nested
    @DisplayName("가장 최근 판정이 2 완제일 때")
    class 완제 {

        @Test
        @DisplayName("빈 목록과 0원을 돌려주고 아무것도 조회하지 않는다")
        void 갚을_것이_없다() {
            // when
            RepayableAmountResponse result = repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.PAID_OFF);

            // then
            assertThat(result.getTargets()).isEmpty();
            assertThat(result.getTotalAmount()).isEqualByComparingTo("0");
            then(repaymentTargetMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("가장 최근 판정이 0 정상·11·21 연체일 때")
    class 회차_방식 {

        @Test
        @DisplayName("0 정상에서 3회가 이자 700원·원금 500,000원 남았으면 회차 이자 → 회차 원금 순이고 합은 500,700원이다")
        void 회차_안에서_이자_다음_원금이다() {
            // given
            given(repaymentTargetMapper.findUnpaidInstallmentDelinquencyCharges(LOAN_ACCOUNT_ID)).willReturn(List.of());
            given(repaymentTargetMapper.findUnpaidInstallments(LOAN_ACCOUNT_ID))
                    .willReturn(List.of(installment(13L, "700", "500000")));

            // when
            RepayableAmountResponse result = repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:INSTALLMENT_INTEREST:13:700",
                    "2:INSTALLMENT_PRINCIPAL:13:500000");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("500700");
        }

        // 상환 4단계 충당 순서: 연체금 → '연체' 회차 → '납부 기한 도래' 회차
        @Test
        @DisplayName("21 연체에서 연체금 300원·300원, 2회(연체) 50,000원·500,000원, 3회(도래) 700원·500,000원이면 연체금 2개 → 2회 → 3회 순이고 합은 1,051,300원이다")
        void 연체금_다음_회차_순서다() {
            // given
            given(repaymentTargetMapper.findUnpaidInstallmentDelinquencyCharges(LOAN_ACCOUNT_ID))
                    .willReturn(List.of(dailyCharge(81L, "300", "0"), dailyCharge(85L, "300", "0")));
            given(repaymentTargetMapper.findUnpaidInstallments(LOAN_ACCOUNT_ID))
                    .willReturn(List.of(installment(12L, "50000", "500000"), installment(13L, "700", "500000")));

            // when
            RepayableAmountResponse result =
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_OVERDUE);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:INSTALLMENT_DELINQUENCY_CHARGE:81:300",
                    "2:INSTALLMENT_DELINQUENCY_CHARGE:85:300",
                    "3:INSTALLMENT_INTEREST:12:50000",
                    "4:INSTALLMENT_PRINCIPAL:12:500000",
                    "5:INSTALLMENT_INTEREST:13:700",
                    "6:INSTALLMENT_PRINCIPAL:13:500000");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("1051300");
        }

        @Test
        @DisplayName("11 이자연체에서 연체 회차가 이자 30,000원·원금 0원 남았으면 회차 이자 30,000원만 담는다")
        void 남은_금액_0원_항목은_넣지_않는다() {
            // given
            given(repaymentTargetMapper.findUnpaidInstallmentDelinquencyCharges(LOAN_ACCOUNT_ID)).willReturn(List.of());
            given(repaymentTargetMapper.findUnpaidInstallments(LOAN_ACCOUNT_ID))
                    .willReturn(List.of(installment(12L, "30000", "0")));

            // when
            RepayableAmountResponse result =
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);

            // then
            assertThat(targetsOf(result)).containsExactly("1:INSTALLMENT_INTEREST:12:30000");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("가장 최근 판정이 12·13·22·23 기한이익상실일 때")
    class 기한이익상실 {

        // 상환 4단계: 연체금(옮긴 → 하루) → 이자(옮긴 → 하루 약정이자) → 원금(옮긴 밀린 원금 → 나머지)
        @Test
        @DisplayName("22에서 옮긴 연체금 1,000원, 하루 연체금 100원·110원, 옮긴 이자 20,000원, 옮긴 밀린 원금 300,000원, 원금잔액 3,000,000원이면 나머지 원금은 2,700,000원이다")
        void 기한이익상실_순서대로_담는다() {
            // given
            given(repaymentTargetMapper.findUnpaidAcceleration(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(acceleration("1000", "20000", "300000", "3000000")));
            given(repaymentTargetMapper.findUnpaidAccelerationDailyCharges(ACCELERATION_ID))
                    .willReturn(List.of(dailyCharge(31L, "100", "0"), dailyCharge(32L, "110", "0")));

            // when
            RepayableAmountResponse result =
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:ACCELERATION_CARRIED_DELINQUENCY_CHARGE:7:1000",
                    "2:ACCELERATION_DAILY_DELINQUENCY_CHARGE:31:100",
                    "3:ACCELERATION_DAILY_DELINQUENCY_CHARGE:32:110",
                    "4:ACCELERATION_CARRIED_INTEREST:7:20000",
                    "5:ACCELERATION_CARRIED_OVERDUE_PRINCIPAL:7:300000",
                    "6:ACCELERATION_REMAINING_PRINCIPAL:10:2700000");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("3021210");
        }

        @Test
        @DisplayName("23 ❻항에서 하루 기록 2행에 연체금 50원·50원과 약정이자 400원·400원이 함께 있으면 하루 연체금은 옮긴 이자 앞, 하루 약정이자는 옮긴 이자 뒤에 온다")
        void 하루_기록의_연체금과_약정이자는_따로_들어간다() {
            // given
            given(repaymentTargetMapper.findUnpaidAcceleration(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(acceleration("1000", "20000", "300000", "3000000")));
            given(repaymentTargetMapper.findUnpaidAccelerationDailyCharges(ACCELERATION_ID))
                    .willReturn(List.of(dailyCharge(31L, "50", "400"), dailyCharge(32L, "50", "400")));

            // when
            RepayableAmountResponse result = repayableAmountService.calculate(
                    LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:ACCELERATION_CARRIED_DELINQUENCY_CHARGE:7:1000",
                    "2:ACCELERATION_DAILY_DELINQUENCY_CHARGE:31:50",
                    "3:ACCELERATION_DAILY_DELINQUENCY_CHARGE:32:50",
                    "4:ACCELERATION_CARRIED_INTEREST:7:20000",
                    "5:ACCELERATION_DAILY_INTEREST:31:400",
                    "6:ACCELERATION_DAILY_INTEREST:32:400",
                    "7:ACCELERATION_CARRIED_OVERDUE_PRINCIPAL:7:300000",
                    "8:ACCELERATION_REMAINING_PRINCIPAL:10:2700000");
        }

        @Test
        @DisplayName("22에서 옮긴 금액을 모두 냈고 하루 연체금 100원, 원금잔액 3,000,000원이면 하루 연체금과 나머지 원금 3,000,000원만 담는다")
        void 다_낸_옮긴_금액은_빠진다() {
            // given
            given(repaymentTargetMapper.findUnpaidAcceleration(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(acceleration("0", "0", "0", "3000000")));
            given(repaymentTargetMapper.findUnpaidAccelerationDailyCharges(ACCELERATION_ID))
                    .willReturn(List.of(dailyCharge(31L, "100", "0")));

            // when
            RepayableAmountResponse result =
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:ACCELERATION_DAILY_DELINQUENCY_CHARGE:31:100",
                    "2:ACCELERATION_REMAINING_PRINCIPAL:10:3000000");
        }

        @Test
        @DisplayName("12인데 기한이익상실 전환 기록이 없으면 LoanAccelerationNotFoundException을 던진다")
        void 전환_기록이_없으면_예외를_던진다() {
            // given
            given(repaymentTargetMapper.findUnpaidAcceleration(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.BULLET_ACCELERATION))
                    .isInstanceOf(LoanAccelerationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("가장 최근 판정이 1 만기 경과일 때")
    class 만기_경과 {

        // 상환 4단계: 연체금(옮긴 → 하루) → 옮긴 이자 → 원금잔액
        @Test
        @DisplayName("옮긴 연체금 500원, 하루 연체금 200원·200원, 옮긴 이자 78,000원, 원금잔액 1,002,000원이면 그 순서이고 합은 1,080,900원이다")
        void 만기_경과_순서대로_담는다() {
            // given
            given(repaymentTargetMapper.findUnpaidMaturityOverdue(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(maturityOverdue("500", "78000", "1002000")));
            given(repaymentTargetMapper.findUnpaidMaturityOverdueDailyCharges(MATURITY_OVERDUE_ID))
                    .willReturn(List.of(dailyCharge(41L, "200", "0"), dailyCharge(42L, "200", "0")));

            // when
            RepayableAmountResponse result =
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.MATURITY_OVERDUE);

            // then
            assertThat(targetsOf(result)).containsExactly(
                    "1:MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE:8:500",
                    "2:MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:41:200",
                    "3:MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE:42:200",
                    "4:MATURITY_OVERDUE_CARRIED_INTEREST:8:78000",
                    "5:MATURITY_OVERDUE_PRINCIPAL:10:1002000");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("1080900");
        }

        @Test
        @DisplayName("만기 경과 전환 기록이 없으면 LoanMaturityOverdueNotFoundException을 던진다")
        void 전환_기록이_없으면_예외를_던진다() {
            // given
            given(repaymentTargetMapper.findUnpaidMaturityOverdue(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    repayableAmountService.calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.MATURITY_OVERDUE))
                    .isInstanceOf(LoanMaturityOverdueNotFoundException.class);
        }
    }

    // "충당 순서:종류:대상 id:금액"으로 바꿔 비교한다
    private List<String> targetsOf(RepayableAmountResponse result) {
        return result.getTargets().stream()
                .map(target -> target.getRepaymentOrder() + ":" + target.getType() + ":" + target.getTargetId() + ":"
                        + target.getAmount().stripTrailingZeros().toPlainString())
                .toList();
    }

    private UnpaidInstallmentResponse installment(Long id, String interest, String principal) {
        return new UnpaidInstallmentResponse(id, new BigDecimal(interest), new BigDecimal(principal));
    }

    private UnpaidDailyChargeResponse dailyCharge(Long id, String delinquencyCharge, String interest) {
        return new UnpaidDailyChargeResponse(id, new BigDecimal(delinquencyCharge), new BigDecimal(interest));
    }

    private UnpaidAccelerationResponse acceleration(String delinquencyCharge, String interest,
                                                    String overduePrincipal, String principalBalance) {
        return new UnpaidAccelerationResponse(ACCELERATION_ID, new BigDecimal(delinquencyCharge),
                new BigDecimal(interest), new BigDecimal(overduePrincipal), new BigDecimal(principalBalance));
    }

    private UnpaidMaturityOverdueResponse maturityOverdue(String delinquencyCharge, String interest,
                                                          String principalBalance) {
        return new UnpaidMaturityOverdueResponse(MATURITY_OVERDUE_ID, new BigDecimal(delinquencyCharge),
                new BigDecimal(interest), new BigDecimal(principalBalance));
    }
}
