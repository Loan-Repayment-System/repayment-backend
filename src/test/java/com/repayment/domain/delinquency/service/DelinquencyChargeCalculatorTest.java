package com.repayment.domain.delinquency.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("연체 이자율·하루 연체금·하루 약정이자 계산")
class DelinquencyChargeCalculatorTest {

    private final DelinquencyChargeCalculator calculator = new DelinquencyChargeCalculator();

    @Nested
    @DisplayName("연체 이자율을 계산할 때")
    class 연체_이자율 {

        // 프로그램 설계 4-2: 15% 미만이면 3%p를 더하되 최고 15%
        @Test
        @DisplayName("대출이자율 5%면 3%p를 더한 8%다")
        void 대출이자율에_3퍼센트포인트를_더한다() {
            // given
            BigDecimal loanRate = new BigDecimal("5");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("8");
        }

        @Test
        @DisplayName("대출이자율 12%면 3%p를 더해 최고 15%와 같은 15%다")
        void 더한_값이_최고_15퍼센트와_같으면_15퍼센트다() {
            // given
            BigDecimal loanRate = new BigDecimal("12");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("대출이자율 13%면 3%p를 더한 16%가 아니라 최고 15%다")
        void 더한_값이_15퍼센트를_넘으면_15퍼센트로_막는다() {
            // given
            BigDecimal loanRate = new BigDecimal("13");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("대출이자율 14.99%면 최고 15%다")
        void 대출이자율이_15퍼센트_바로_아래여도_15퍼센트로_막는다() {
            // given
            BigDecimal loanRate = new BigDecimal("14.99");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("15");
        }

        // 프로그램 설계 4-2: 대출이자율이 15% 이상이면 2%p를 더한다
        @Test
        @DisplayName("대출이자율 15%면 2%p를 더한 17%다")
        void 대출이자율이_15퍼센트면_2퍼센트포인트를_더한다() {
            // given
            BigDecimal loanRate = new BigDecimal("15");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("17");
        }

        @Test
        @DisplayName("대출이자율 16%면 15%로 막지 않고 2%p를 더한 18%다")
        void 대출이자율이_15퍼센트를_넘으면_2퍼센트포인트를_더한다() {
            // given
            BigDecimal loanRate = new BigDecimal("16");

            // when
            BigDecimal result = calculator.calculateDelinquencyRate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("18");
        }
    }

    @Nested
    @DisplayName("하루 연체금을 계산할 때")
    class 하루_연체금 {

        // 프로그램 설계 4-2: 대상 금액 × 연체 이자율 ÷ 365, 원 미만 버림
        @Test
        @DisplayName("대상 금액 1,000,000원, 연체 이자율 8%면 219.17…원에서 원 미만을 버린 219원이다")
        void 원_미만을_버린다() {
            // given
            BigDecimal targetAmount = new BigDecimal("1000000");
            BigDecimal delinquencyRate = new BigDecimal("8");

            // when
            BigDecimal result = calculator.calculateDailyCharge(targetAmount, delinquencyRate);

            // then
            assertThat(result).isEqualByComparingTo("219");
        }

        @Test
        @DisplayName("대상 금액 365,000원, 연체 이자율 10%면 나누어떨어져 100원이다")
        void 나누어떨어지면_그대로_돌려준다() {
            // given
            BigDecimal targetAmount = new BigDecimal("365000");
            BigDecimal delinquencyRate = new BigDecimal("10");

            // when
            BigDecimal result = calculator.calculateDailyCharge(targetAmount, delinquencyRate);

            // then
            assertThat(result).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("대상 금액 0원이면 연체금은 0원이다")
        void 대상_금액이_0원이면_0원이다() {
            // given
            BigDecimal targetAmount = BigDecimal.ZERO;
            BigDecimal delinquencyRate = new BigDecimal("8");

            // when
            BigDecimal result = calculator.calculateDailyCharge(targetAmount, delinquencyRate);

            // then
            assertThat(result).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("하루 약정이자를 계산할 때")
    class 하루_약정이자 {

        // 프로그램 설계 4-4: 7 기한이익상실+❻의 나머지 원금 × 대출이자율 ÷ 365, 원 미만 버림
        @Test
        @DisplayName("원금 28,700,000원, 대출이자율 5%면 3,931.50…원에서 원 미만을 버린 3,931원이다")
        void 대출이자율로_계산하고_원_미만을_버린다() {
            // given
            BigDecimal principal = new BigDecimal("28700000");
            BigDecimal loanRate = new BigDecimal("5");

            // when
            BigDecimal result = calculator.calculateDailyInterest(principal, loanRate);

            // then
            assertThat(result).isEqualByComparingTo("3931");
        }

        @Test
        @DisplayName("대출이자율 0%면 약정이자는 0원이다")
        void 대출이자율이_0퍼센트면_0원이다() {
            // given
            BigDecimal principal = new BigDecimal("28700000");
            BigDecimal loanRate = BigDecimal.ZERO;

            // when
            BigDecimal result = calculator.calculateDailyInterest(principal, loanRate);

            // then
            assertThat(result).isEqualByComparingTo("0");
        }
    }
}
