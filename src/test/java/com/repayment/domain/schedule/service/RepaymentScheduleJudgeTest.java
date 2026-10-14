package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("회차 판정 규칙")
class RepaymentScheduleJudgeTest {

    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 11, 10);

    private final RepaymentScheduleJudge judge = new RepaymentScheduleJudge();

    @Nested
    @DisplayName("끝 상태인 회차일 때")
    class 끝_상태 {

        @Test
        @DisplayName("납입 완료 회차는 11/20에 판정해도 그대로다")
        void 납입_완료는_그대로다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.PAID, "1000000", "50000", "1000000", "50000");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 20));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.KEEP);
        }

        @Test
        @DisplayName("기한이익상실로 종료된 회차는 남은 금액이 있고 예정일이 지나도 그대로다")
        void 기한이익상실로_종료는_연체로_되살리지_않는다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.CLOSED_BY_ACCELERATION, "1000000", "50000", "0", "0");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 20));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.KEEP);
        }

        @Test
        @DisplayName("만기 경과로 종료된 회차는 남은 금액이 있고 예정일이 지나도 그대로다")
        void 만기_경과로_종료는_연체로_되살리지_않는다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.CLOSED_BY_MATURITY_OVERDUE, "1000000", "50000", "0", "0");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 20));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.KEEP);
        }
    }

    @Nested
    @DisplayName("금액이 확정되지 않은 회차일 때 (예정일 11/10)")
    class 미확정 {

        @Test
        @DisplayName("11/04(예정일 6일 전)에는 아직 확정하지 않고 그대로다")
        void 예정일_5일_전보다_이르면_그대로다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 4));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.KEEP);
        }

        @Test
        @DisplayName("11/05(예정일 5일 전)이면 확정한다")
        void 예정일_5일_전이면_확정한다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 5));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.CONFIRM);
        }

        @Test
        @DisplayName("11/10(예정일 당일)이면 늦었어도 확정한다")
        void 예정일_당일까지는_따라잡아_확정한다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 10));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.CONFIRM);
        }

        @Test
        @DisplayName("11/11(예정일 다음 날)이면 연체로 만들지 않고 미확정 예정일 지남으로 판정한다")
        void 예정일이_지났으면_연체가_아니라_실패다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 11));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.UNCONFIRMED_PAST_DUE);
        }
    }

    @Nested
    @DisplayName("금액이 확정된 회차일 때 (예정일 11/10, 원금 1,000,000원, 이자 50,000원)")
    class 확정 {

        @Test
        @DisplayName("11/07에 남은 금액이 0원이면 예정일 전이어도 납입 완료다")
        void 예정일_전에_다_냈으면_납입_완료다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.DUE, "1000000", "50000", "1000000", "50000");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 7));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.MARK_PAID);
        }

        @Test
        @DisplayName("11/20에 남은 금액이 0원이면 예정일이 지났어도 연체가 아니라 납입 완료다")
        void 예정일이_지나도_다_냈으면_납입_완료다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.OVERDUE, "1000000", "50000", "1000000", "50000");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 20));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.MARK_PAID);
        }

        @Test
        @DisplayName("11/10(예정일 당일)에 이자 50,000원만 냈으면 아직 연체가 아니라 납부 기한 도래다")
        void 예정일_당일은_납부_기한_도래다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.DUE, "1000000", "50000", "0", "50000");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 10));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.MARK_DUE);
        }

        @Test
        @DisplayName("11/11(예정일 다음 날)에 이자 50,000원만 냈으면 연체다")
        void 예정일_다음_날부터_연체다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target =
                    confirmed(RepaymentScheduleStatus.DUE, "1000000", "50000", "0", "50000");

            // when
            RepaymentScheduleJudgment result = judge.judge(target, LocalDate.of(2026, 11, 11));

            // then
            assertThat(result).isEqualTo(RepaymentScheduleJudgment.MARK_OVERDUE);
        }
    }

    private RepaymentScheduleJudgmentTargetResponse unconfirmed() {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(100L)
                .loanAccountId(10L)
                .repaymentScheduleStatusId(RepaymentScheduleStatus.SCHEDULED)
                .scheduledDate(SCHEDULED_DATE)
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(BigDecimal.ZERO)
                .build();
    }

    private RepaymentScheduleJudgmentTargetResponse confirmed(RepaymentScheduleStatus status,
                                                              String principal, String interest,
                                                              String paidPrincipal, String paidInterest) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(100L)
                .loanAccountId(10L)
                .repaymentScheduleStatusId(status)
                .scheduledDate(SCHEDULED_DATE)
                .confirmedAt(LocalDateTime.of(2026, 11, 5, 0, 0))
                .scheduledPrincipal(new BigDecimal(principal))
                .scheduledInterest(new BigDecimal(interest))
                .paidPrincipal(new BigDecimal(paidPrincipal))
                .paidInterest(new BigDecimal(paidInterest))
                .build();
    }
}
