package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.AutoDebitRepaymentJobRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("자동 이체 상환 스케줄 실행")
class AutoDebitRepaymentSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

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

    @Nested
    @DisplayName("Job이 끝난 뒤 로그를 남길 때")
    class 종료_로그 {

        // 실패 대처 case 1
        @Test
        @DisplayName("Job 실행 7이 커넥션 획득 실패로 실패하면 기준일, 상태, 실행 ID, 실패 원인을 에러 로그로 남긴다")
        void 실패하면_에러_로그를_남긴다(CapturedOutput output) throws Exception {
            // given
            JobExecution execution = jobExecution(7L, BatchStatus.FAILED);
            execution.addFailureException(new CannotGetJdbcConnectionException("커넥션 획득 실패"));
            given(autoDebitRepaymentJobRunner.run()).willReturn(execution);

            // when
            scheduler.run();

            // then
            assertThat(output.getOut())
                    .contains("ERROR")
                    .contains("자동 이체 상환 Job 미완료 businessDate=2026-10-17 status=FAILED jobExecutionId=7")
                    .contains(CannotGetJdbcConnectionException.class.getName() + ": 커넥션 획득 실패");
        }

        // 실패 대처 case 1
        @Test
        @DisplayName("Job이 완료되면 에러 로그를 남기지 않는다")
        void 완료되면_에러_로그를_남기지_않는다(CapturedOutput output) throws Exception {
            // given
            given(autoDebitRepaymentJobRunner.run()).willReturn(jobExecution(7L, BatchStatus.COMPLETED));

            // when
            scheduler.run();

            // then
            assertThat(output.getOut()).doesNotContain("ERROR");
        }

        private JobExecution jobExecution(long id, BatchStatus status) {
            JobExecution execution = new JobExecution(new JobInstance(1L, "autoDebitRepaymentJob"), id,
                    new JobParametersBuilder().addLocalDate("businessDate", TODAY).toJobParameters());
            execution.setStatus(status);
            return execution;
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
