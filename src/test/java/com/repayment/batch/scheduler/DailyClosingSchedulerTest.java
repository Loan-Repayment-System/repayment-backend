package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.DailyClosingJobRunner;
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
@DisplayName("일마감 스케줄 실행")
class DailyClosingSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private DailyClosingJobRunner dailyClosingJobRunner;

    @InjectMocks
    private DailyClosingScheduler scheduler;

    @Nested
    @DisplayName("스케줄이 실행될 때")
    class 실행 {

        @Test
        @DisplayName("Job을 실행하다 예외가 나면 밖으로 던지지 않고 다시 실행하지 않는다")
        void 예외가_나도_다시_실행하지_않는다() throws Exception {
            // given
            given(dailyClosingJobRunner.run()).willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

            // when
            Throwable thrown = catchThrowable(scheduler::run);

            // then
            assertThat(thrown).isNull();
            then(dailyClosingJobRunner).should(times(1)).run();
        }
    }

    @Nested
    @DisplayName("실행 시각을 계산할 때")
    class 실행_시각 {

        @Test
        @DisplayName("서울 10/23 23:59:59 다음 실행 시각은 서울 10/24 00:00:00이다")
        void 매일_서울_자정에_실행한다() throws Exception {
            // given
            Scheduled scheduled = DailyClosingScheduler.class.getMethod("run").getAnnotation(Scheduled.class);
            ZonedDateTime beforeMidnight = ZonedDateTime.of(2026, 10, 23, 23, 59, 59, 0, ZoneId.of(scheduled.zone()));

            // when
            ZonedDateTime next = CronExpression.parse(scheduled.cron()).next(beforeMidnight);

            // then
            assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, SEOUL));
        }
    }
}
