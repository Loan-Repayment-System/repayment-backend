package com.repayment.domain.loan.service;

import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대출 상태 판정 트리")
class LoanStatusJudgeTest {

    private static final LocalDate MATURITY_DATE = LocalDate.of(2027, 6, 30);
    private static final LocalDate AFTER_MATURITY = MATURITY_DATE.plusDays(1);
    // ❻항 미적용 / 적용
    private static final String PRINCIPAL_100M = "100000000";
    private static final String PRINCIPAL_30M = "30000000";

    private final LoanStatusJudge judge = new LoanStatusJudge();

    @Nested
    @DisplayName("다 갚았을 때")
    class 완제 {

        @Test
        @DisplayName("직전이 6 기한이익상실이고 밀린 회차가 2개여도 다 갚았으면 8 완제다")
        void 완제를_가장_먼저_본다() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_ACCELERATION,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 2, LocalDate.of(2027, 1, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, true, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.PAID_OFF);
        }
    }

    @Nested
    @DisplayName("직전 판정이 2·3·6·7 기한이익상실일 때")
    class 직전_기한이익상실 {

        @Test
        @DisplayName("직전 3 만기일시+❻(약정 3천만)이면 만기일(6/30) 당일에는 3 그대로다")
        void 만기일_당일은_6항_유지() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6,
                    RepaymentMethod.BULLET, PRINCIPAL_30M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, MATURITY_DATE);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6);
        }

        @Test
        @DisplayName("직전 3 만기일시+❻이면 만기 다음 날(7/1) ❻항이 풀려 4 만기 경과가 아니라 2 기한이익상실이다")
        void 만기가_지나면_3에서_2로() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6,
                    RepaymentMethod.BULLET, PRINCIPAL_30M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, AFTER_MATURITY);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION);
        }

        @Test
        @DisplayName("직전 2 만기일시 기한이익상실(약정 1억)이면 2 그대로다")
        void 기한이익상실_2는_그대로() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_ACCELERATION,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION);
        }

        @Test
        @DisplayName("직전 7 분할상환+❻(약정 3천만)이면 만기 전에는 7 그대로다")
        void 분할상환도_만기_전에는_6항_유지() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_30M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);
        }

        @Test
        @DisplayName("직전 7 분할상환+❻이면 만기 다음 날(7/1) 6 기한이익상실이다")
        void 만기가_지나면_7에서_6으로() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_30M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, AFTER_MATURITY);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
        }

        @Test
        @DisplayName("직전 6 분할상환 기한이익상실이면 밀린 회차가 0개여도 0 정상이 아니라 6이다")
        void 회차를_보지_않는다() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_ACCELERATION,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
        }
    }

    @Nested
    @DisplayName("직전 판정이 4 만기 경과일 때")
    class 직전_만기_경과 {

        @Test
        @DisplayName("밀린 회차가 0개여도 4 만기 경과 그대로다")
        void 만기_경과는_그대로() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.MATURITY_OVERDUE,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 7, 10));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.MATURITY_OVERDUE);
        }
    }

    @Nested
    @DisplayName("직전 판정이 0 정상·1 이자연체·5 1회 연체여서 밀린 회차로 판정할 때")
    class 회차로_판정 {

        @Test
        @DisplayName("직전 0 정상이고 밀린 회차가 0개면 0 정상이다")
        void 밀린_회차가_없으면_정상() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.NORMAL,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.NORMAL);
        }

        @Test
        @DisplayName("직전 1 이자연체여도 밀린 회차를 다 갚아 0개면 0 정상이다")
        void 밀린_회차를_다_갚으면_정상으로_돌아온다() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 0, null);

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.NORMAL);
        }

        @Test
        @DisplayName("만기일시가 1회 밀리고 만기 다음 날(7/1)이면 4 만기 경과다")
        void 만기가_지나면_만기일시도_만기_경과() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.NORMAL,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 1, LocalDate.of(2027, 6, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, AFTER_MATURITY);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.MATURITY_OVERDUE);
        }

        @Test
        @DisplayName("분할상환이 2회 밀렸어도(약정 3천만) 만기 다음 날(7/1)이면 기한이익상실이 아니라 4 만기 경과다")
        void 만기_판정이_기한이익상실보다_먼저() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_OVERDUE,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_30M, 2, LocalDate.of(2027, 5, 31));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, AFTER_MATURITY);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.MATURITY_OVERDUE);
        }

        @Test
        @DisplayName("분할상환이 1회 밀리고 만기일(6/30) 당일이면 만기 전이라 5 1회 연체다")
        void 만기일_당일은_만기_전() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.NORMAL,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 1, LocalDate.of(2027, 5, 31));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, MATURITY_DATE);

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_OVERDUE);
        }

        // 1개월 = 예정일의 다음 달 같은 날까지 (민법 157조·160조)
        @Test
        @DisplayName("만기일시가 3/10 예정일부터 밀렸으면 1개월 되는 4/10 당일까지 1 이자연체다")
        void 예정일_1개월_당일까지_이자연체() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 1, LocalDate.of(2027, 3, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 4, 10));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);
        }

        @Test
        @DisplayName("만기일시가 3/10 예정일부터 밀렸으면 4/11부터 2 기한이익상실이다(약정 1억)")
        void 예정일_1개월_다음_날부터_기한이익상실() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 1, LocalDate.of(2027, 3, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 4, 11));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION);
        }

        @Test
        @DisplayName("만기일시가 3/10 예정일부터 밀렸고 약정 3천만이면 4/11부터 3 기한이익상실+❻이다")
        void 만기일시_6항이면_3() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_30M, 1, LocalDate.of(2027, 3, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 4, 11));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6);
        }

        // 1개월 끝 날이 없는 달은 그 달 말일까지 (민법 160조 ③)
        @Test
        @DisplayName("만기일시가 1/31 예정일부터 밀렸으면 2027-02-28(평년 말일)까지 1 이자연체다")
        void 같은_날이_없는_달은_말일까지_이자연체() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 1, LocalDate.of(2027, 1, 31));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 2, 28));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);
        }

        @Test
        @DisplayName("만기일시가 1/31 예정일부터 밀렸으면 3/1부터 2 기한이익상실이다")
        void 말일_다음_날부터_기한이익상실() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
                    RepaymentMethod.BULLET, PRINCIPAL_100M, 1, LocalDate.of(2027, 1, 31));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 1));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.BULLET_ACCELERATION);
        }

        @Test
        @DisplayName("원리금균등이 1회 밀렸으면 5 1회 연체다")
        void 분할상환_1회면_1회_연체() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.NORMAL,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_100M, 1, LocalDate.of(2027, 2, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_OVERDUE);
        }

        @Test
        @DisplayName("원금균등이 2회 밀렸으면(약정 1억) 6 분할상환 기한이익상실이다")
        void 원금균등도_2회면_기한이익상실() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_OVERDUE,
                    RepaymentMethod.EQUAL_PRINCIPAL, PRINCIPAL_100M, 2, LocalDate.of(2027, 2, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
        }

        @Test
        @DisplayName("원리금균등이 2회 밀렸고 약정 3천만이면 7 기한이익상실+❻이다")
        void 분할상환_6항이면_7() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_OVERDUE,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, PRINCIPAL_30M, 2, LocalDate.of(2027, 2, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);
        }

        // ❻항은 약정금액 5천만 원 "미만"만
        @Test
        @DisplayName("원리금균등이 2회 밀렸고 약정이 정확히 5천만이면 ❻항이 아니라 6이다")
        void 약정_5천만은_6항_아님() {
            // given
            LoanStatusJudgmentTargetResponse target = target(LoanJudgmentStatus.INSTALLMENT_OVERDUE,
                    RepaymentMethod.EQUAL_PRINCIPAL_INTEREST, "50000000", 2, LocalDate.of(2027, 2, 10));

            // when
            LoanJudgmentStatus result = judge.judge(target, false, LocalDate.of(2027, 3, 15));

            // then
            assertThat(result).isEqualTo(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
        }
    }

    private LoanStatusJudgmentTargetResponse target(LoanJudgmentStatus previous, RepaymentMethod method,
                                                    String contractPrincipal, int overdueCount,
                                                    LocalDate oldestOverdueDate) {
        return LoanStatusJudgmentTargetResponse.builder()
                .loanAccountId(1L)
                .previousJudgmentStatus(previous)
                .repaymentMethodId(method)
                .contractPrincipal(new BigDecimal(contractPrincipal))
                .maturityDate(MATURITY_DATE)
                .overdueInstallmentCount(overdueCount)
                .oldestOverdueDate(oldestOverdueDate)
                .build();
    }
}
