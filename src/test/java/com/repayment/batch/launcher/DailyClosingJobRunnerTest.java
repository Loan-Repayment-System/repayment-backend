package com.repayment.batch.launcher;

import com.repayment.domain.closing.dto.response.DailyClosingRunResponse;
import com.repayment.domain.closing.exception.DailyClosingAlreadyCompletedException;
import com.repayment.domain.closing.exception.DailyClosingFailedException;
import com.repayment.global.code.DailyClosingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("일마감 Job 실행")
class DailyClosingJobRunnerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 서울 2026-10-24 00:00 = UTC 2026-10-23 15:00
    private static final Clock MIDNIGHT_IN_SEOUL = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), SEOUL);

    // 서울 2026-10-24 14:30
    private static final Clock AFTERNOON_IN_SEOUL = Clock.fixed(Instant.parse("2026-10-24T05:30:00Z"), SEOUL);

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 24);

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job dailyClosingJob;

    @Nested
    @DisplayName("스케줄로 실행할 때")
    class 스케줄_실행 {

        @Test
        @DisplayName("서울 10/24 00:00(UTC 10/23 15:00)이면 기준일 10/24, 요청 시각 10/24 00:00으로 일마감 Job을 실행한다")
        void 서울_날짜를_기준일로_Job을_실행한다() throws Exception {
            // given
            DailyClosingJobRunner runner = new DailyClosingJobRunner(jobLauncher, dailyClosingJob, MIDNIGHT_IN_SEOUL);
            ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);

            // when
            runner.run();

            // then
            then(jobLauncher).should().run(eq(dailyClosingJob), parameters.capture());
            assertThat(parameters.getValue().getLocalDate("businessDate")).isEqualTo(BUSINESS_DATE);
            assertThat(parameters.getValue().getLocalDateTime("requestedAt"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 24, 0, 0));
        }
    }

    @Nested
    @DisplayName("수동으로 실행할 때")
    class 수동_실행 {

        @Test
        @DisplayName("서울 10/24 14:30에 Job이 완료되면 기준일 10/24, 상태 완료를 돌려준다")
        void 완료되면_오늘_기준일과_완료를_돌려준다() throws Exception {
            // given
            DailyClosingJobRunner runner = new DailyClosingJobRunner(jobLauncher, dailyClosingJob, AFTERNOON_IN_SEOUL);
            given(jobLauncher.run(eq(dailyClosingJob), any())).willReturn(execution(BatchStatus.COMPLETED));

            // when
            DailyClosingRunResponse result = runner.runManually();

            // then
            assertThat(result.getBusinessDate()).isEqualTo(BUSINESS_DATE);
            assertThat(result.getStatus()).isEqualTo(DailyClosingStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 완료된 일마감이라 Job이 실패하면 그 업무 예외를 그대로 던진다")
        void 업무_예외로_실패하면_그_예외를_던진다() throws Exception {
            // given
            DailyClosingJobRunner runner = new DailyClosingJobRunner(jobLauncher, dailyClosingJob, AFTERNOON_IN_SEOUL);
            DailyClosingAlreadyCompletedException cause = new DailyClosingAlreadyCompletedException(BUSINESS_DATE);
            JobExecution failed = execution(BatchStatus.FAILED);
            failed.addFailureException(cause);
            given(jobLauncher.run(eq(dailyClosingJob), any())).willReturn(failed);

            // when
            Throwable thrown = catchThrowable(runner::runManually);

            // then
            assertThat(thrown).isSameAs(cause);
        }

        @Test
        @DisplayName("업무 예외가 아닌 IllegalStateException으로 Job이 실패하면 그 예외를 원인으로 일마감 실패를 던진다")
        void 업무_예외가_아니면_일마감_실패로_감싼다() throws Exception {
            // given
            DailyClosingJobRunner runner = new DailyClosingJobRunner(jobLauncher, dailyClosingJob, AFTERNOON_IN_SEOUL);
            IllegalStateException cause = new IllegalStateException("예상하지 못한 오류");
            JobExecution failed = execution(BatchStatus.FAILED);
            failed.addFailureException(cause);
            given(jobLauncher.run(eq(dailyClosingJob), any())).willReturn(failed);

            // when
            Throwable thrown = catchThrowable(runner::runManually);

            // then
            assertThat(thrown).isInstanceOf(DailyClosingFailedException.class).hasCause(cause);
        }

        @Test
        @DisplayName("JobLauncher가 실행을 거절해 JobExecutionAlreadyRunningException을 던지면 그 예외를 원인으로 일마감 실패를 던진다")
        void 실행이_거절되면_일마감_실패로_감싼다() throws Exception {
            // given
            DailyClosingJobRunner runner = new DailyClosingJobRunner(jobLauncher, dailyClosingJob, AFTERNOON_IN_SEOUL);
            JobExecutionAlreadyRunningException cause = new JobExecutionAlreadyRunningException("이미 실행 중");
            given(jobLauncher.run(eq(dailyClosingJob), any())).willThrow(cause);

            // when
            Throwable thrown = catchThrowable(runner::runManually);

            // then
            assertThat(thrown).isInstanceOf(DailyClosingFailedException.class).hasCause(cause);
        }
    }

    private static JobExecution execution(BatchStatus status) {
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(status);
        return execution;
    }
}
