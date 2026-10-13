package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.global.code.RepaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("상환 방법별 회차 원금·이자 계산")
class RepaymentAmountCalculatorTest {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("6");

    private final RepaymentAmountCalculator calculator = new RepaymentAmountCalculator();

    @Nested
    @DisplayName("원리금균등상환일 때 (연 6%, 원금 12,000,000원, 12회)")
    class 원리금균등상환 {

        private static final BigDecimal PRINCIPAL = new BigDecimal("12000000");
        private static final int TOTAL = 12;

        @Test
        @DisplayName("첫 회차(잔액 12,000,000원, 남은 12회)는 원금 972,797원, 이자 60,000원이다")
        void 첫_회차는_원리금균등_공식으로_계산하고_원_미만을_버린다() {
            // given
            BigDecimal balance = new BigDecimal("12000000");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, balance, ANNUAL_RATE, 12, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("972797");
            assertThat(result.getInterest()).isEqualByComparingTo("60000");
        }

        @Test
        @DisplayName("2회차(잔액 11,027,203원, 남은 11회)는 이자가 55,136원으로 줄고 원금이 977,661원으로 는다")
        void 중간_회차는_줄어든_이자만큼_원금이_는다() {
            // given
            BigDecimal balance = new BigDecimal("11027203");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, balance, ANNUAL_RATE, 11, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("977661");
            assertThat(result.getInterest()).isEqualByComparingTo("55136");
        }

        // 프로그램 설계 상환금 확정: 마지막 회차는 남은 B를 모두 원금으로 한다
        @Test
        @DisplayName("마지막 회차(잔액 1,027,660원)는 잔액 전부를 원금으로, 이자 5,138원이다")
        void 마지막_회차는_잔액_전부를_원금으로_한다() {
            // given
            BigDecimal balance = new BigDecimal("1027660");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, balance, ANNUAL_RATE, 1, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("1027660");
            assertThat(result.getInterest()).isEqualByComparingTo("5138");
        }

        @Test
        @DisplayName("연 0%면 잔액 1,000,000원을 남은 3회로 나눠 원금 333,333원, 이자 0원이다")
        void 금리가_0이면_잔액을_남은_회차로_나눈다() {
            // given
            BigDecimal balance = new BigDecimal("1000000");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, balance, BigDecimal.ZERO, 3, balance, 3);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("333333");
            assertThat(result.getInterest()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("1~12회차를 차례로 계산하면 원금 합이 12,000,000원이고 마지막 잔액이 0원이다")
        void 원금_합이_최초_원금과_같다() {
            // given
            BigDecimal balance = PRINCIPAL;
            BigDecimal principalSum = BigDecimal.ZERO;

            // when
            for (int remaining = TOTAL; remaining >= 1; remaining--) {
                RepaymentAmountResponse result = calculator.calculate(
                        RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, balance, ANNUAL_RATE, remaining, PRINCIPAL, TOTAL);
                principalSum = principalSum.add(result.getPrincipal());
                balance = balance.subtract(result.getPrincipal());
            }

            // then
            assertThat(principalSum).isEqualByComparingTo("12000000");
            assertThat(balance).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("원금균등상환일 때 (연 6%, 원금 10,000,000원, 3회)")
    class 원금균등상환 {

        private static final BigDecimal PRINCIPAL = new BigDecimal("10000000");
        private static final int TOTAL = 3;

        @Test
        @DisplayName("첫 회차(잔액 10,000,000원)는 원금 3,333,333원, 이자 50,000원이다")
        void 첫_회차는_최초_원금을_총_회차로_나눈다() {
            // given
            BigDecimal balance = new BigDecimal("10000000");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL, balance, ANNUAL_RATE, 3, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("3333333");
            assertThat(result.getInterest()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("중간 회차(잔액 6,666,667원)도 원금은 최초 원금 기준 3,333,333원, 이자 33,333원이다")
        void 중간_회차도_원금은_최초_원금_기준이다() {
            // given
            BigDecimal balance = new BigDecimal("6666667");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL, balance, ANNUAL_RATE, 2, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("3333333");
            assertThat(result.getInterest()).isEqualByComparingTo("33333");
        }

        @Test
        @DisplayName("마지막 회차(잔액 3,333,334원)는 앞에서 버린 1원까지 원금 3,333,334원, 이자 16,666원이다")
        void 마지막_회차가_버린_금액을_메운다() {
            // given
            BigDecimal balance = new BigDecimal("3333334");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.EQUAL_PRINCIPAL, balance, ANNUAL_RATE, 1, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("3333334");
            assertThat(result.getInterest()).isEqualByComparingTo("16666");
        }

        @Test
        @DisplayName("1~3회차를 차례로 계산하면 원금 합이 10,000,000원이다")
        void 원금_합이_최초_원금과_같다() {
            // given
            BigDecimal balance = PRINCIPAL;
            BigDecimal principalSum = BigDecimal.ZERO;

            // when
            for (int remaining = TOTAL; remaining >= 1; remaining--) {
                RepaymentAmountResponse result = calculator.calculate(
                        RepaymentMethod.EQUAL_PRINCIPAL, balance, ANNUAL_RATE, remaining, PRINCIPAL, TOTAL);
                principalSum = principalSum.add(result.getPrincipal());
                balance = balance.subtract(result.getPrincipal());
            }

            // then
            assertThat(principalSum).isEqualByComparingTo("10000000");
            assertThat(balance).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("원금만기일시상환일 때 (연 6%, 3회)")
    class 원금만기일시상환 {

        private static final BigDecimal PRINCIPAL = new BigDecimal("10000000");
        private static final int TOTAL = 3;

        @Test
        @DisplayName("마지막이 아닌 회차(잔액 10,000,000원)는 원금 0원, 이자 50,000원이다")
        void 만기_전에는_이자만_낸다() {
            // given
            BigDecimal balance = new BigDecimal("10000000");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.BULLET, balance, ANNUAL_RATE, 3, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("0");
            assertThat(result.getInterest()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("마지막 회차(잔액 10,000,000원)는 원금 10,000,000원, 이자 50,000원이다")
        void 만기에_원금_전부를_낸다() {
            // given
            BigDecimal balance = new BigDecimal("10000000");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.BULLET, balance, ANNUAL_RATE, 1, PRINCIPAL, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("10000000");
            assertThat(result.getInterest()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("잔액 100원이면 이자 0.5원을 버려 원금 0원, 이자 0원이다")
        void 원_미만_이자는_0원이다() {
            // given
            BigDecimal balance = new BigDecimal("100");

            // when
            RepaymentAmountResponse result = calculator.calculate(
                    RepaymentMethod.BULLET, balance, ANNUAL_RATE, 3, balance, TOTAL);

            // then
            assertThat(result.getPrincipal()).isEqualByComparingTo("0");
            assertThat(result.getInterest()).isEqualByComparingTo("0");
        }
    }
}
