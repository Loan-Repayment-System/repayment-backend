package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.AutoDebitRepaymentJobRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("자동 이체 상환 스케줄 실행")
class AutoDebitRepaymentSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private AutoDebitRepaymentJobRunner autoDebitRepaymentJobRunner;

    @InjectMocks
    private AutoDebitRepaymentScheduler scheduler;

    @Nested
    @DisplayName("스케줄이 실행될 때")
    class 실행 {

        @Test
        @DisplayName("Job을 실행하다 예외가 나면 밖으로 던지지 않고 다시 실행하지 않는다")
        void 예외가_나도_다시_실행하지_않는다() throws Exception {
            // given
            given(autoDebitRepaymentJobRunner.run()).willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

            // when
            Throwable thrown = catchThrowable(scheduler::run);

            // then
            assertThat(thrown).isNull();
            then(autoDebitRepaymentJobRunner).should(times(1)).run();
        }
    }

    // 요구사항 1: 09:00에 처음 출금하고, 13:00·16:00에 다시 출금한다
    @Nested
    @DisplayName("실행 시각을 계산할 때")
    class 실행_시각 {

        @Test
        @DisplayName("서울 10/17 08:59:59 다음 실행 시각은 서울 10/17 09:00:00이다")
        void 아홉_시에_실행한다() throws Exception {
            // given
            ZonedDateTime before = ZonedDateTime.of(2026, 10, 17, 8, 59, 59, 0, SEOUL);

            // when
            ZonedDateTime next = nextRun(before);

            // then
            assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 17, 9, 0, 0, 0, SEOUL));
        }

        @Test
        @DisplayName("서울 10/17 09:00:00 다음 실행 시각은 서울 10/17 13:00:00이다")
        void 열세_시에_실행한다() throws Exception {
            // given
            ZonedDateTime before = ZonedDateTime.of(2026, 10, 17, 9, 0, 0, 0, SEOUL);

            // when
            ZonedDateTime next = nextRun(before);

            // then
            assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 17, 13, 0, 0, 0, SEOUL));
        }

        @Test
        @DisplayName("서울 10/17 13:00:00 다음 실행 시각은 서울 10/17 16:00:00이다")
        void 열여섯_시에_실행한다() throws Exception {
            // given
            ZonedDateTime before = ZonedDateTime.of(2026, 10, 17, 13, 0, 0, 0, SEOUL);

            // when
            ZonedDateTime next = nextRun(before);

            // then
            assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 17, 16, 0, 0, 0, SEOUL));
        }

        @Test
        @DisplayName("서울 10/17 16:00:00 다음 실행 시각은 서울 10/18 09:00:00이다")
        void 다음날_아홉_시에_실행한다() throws Exception {
            // given
            ZonedDateTime before = ZonedDateTime.of(2026, 10, 17, 16, 0, 0, 0, SEOUL);

            // when
            ZonedDateTime next = nextRun(before);

            // then
            assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 18, 9, 0, 0, 0, SEOUL));
        }

        private ZonedDateTime nextRun(ZonedDateTime from) throws Exception {
            Scheduled scheduled = AutoDebitRepaymentScheduler.class.getMethod("run").getAnnotation(Scheduled.class);
            return CronExpression.parse(scheduled.cron()).next(from.withZoneSameInstant(ZoneId.of(scheduled.zone())));
        }
    }
}
