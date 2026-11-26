package com.repayment.batch.launcher;

import com.repayment.domain.repayment.dto.response.AutoDebitRepaymentRunResponse;
import com.repayment.domain.repayment.exception.AutoDebitRepaymentFailedException;
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
import org.springframework.batch.core.StepExecution;
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
@DisplayName("자동 이체 상환 Job 실행")
class AutoDebitRepaymentJobRunnerTest {

    // 서울 2026-10-17 09:00 = UTC 2026-10-17 00:00
    private static final Clock NINE_IN_SEOUL = Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 17);

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job autoDebitRepaymentJob;

    @Nested
    @DisplayName("스케줄로 실행할 때")
    class 스케줄_실행 {

        @Test
        @DisplayName("서울 10/17 09:00(UTC 10/17 00:00)이면 기준일 10/17, 요청 시각 10/17 09:00으로 자동 이체 상환 Job을 실행한다")
        void 서울_날짜를_기준일로_Job을_실행한다() throws Exception {
            // given
            AutoDebitRepaymentJobRunner runner = newRunner();
            ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);

            // when
            runner.run();

            // then
            then(jobLauncher).should().run(eq(autoDebitRepaymentJob), parameters.capture());
            assertThat(parameters.getValue().getLocalDate("businessDate")).isEqualTo(BUSINESS_DATE);
            assertThat(parameters.getValue().getLocalDateTime("requestedAt"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 17, 9, 0));
        }
    }

    @Nested
    @DisplayName("수동으로 실행할 때")
    class 수동_실행 {

        @Test
        @DisplayName("Job이 완료되고 대출 3건을 읽어 2건 출금을 시도했으면 기준일 10/17, 읽음 3, 시도 2를 돌려준다")
        void 완료되면_기준일과_건수를_돌려준다() throws Exception {
            // given
            AutoDebitRepaymentJobRunner runner = newRunner();
            JobExecution completed = execution(BatchStatus.COMPLETED);
            StepExecution step = completed.createStepExecution("autoDebitRepaymentStep");
            step.setReadCount(3);
            step.setWriteCount(2);
            given(jobLauncher.run(eq(autoDebitRepaymentJob), any())).willReturn(completed);

            // when
            AutoDebitRepaymentRunResponse result = runner.runManually();

            // then
            assertThat(result.getBusinessDate()).isEqualTo(BUSINESS_DATE);
            assertThat(result.getReadCount()).isEqualTo(3);
            assertThat(result.getAttemptedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("IllegalStateException으로 Job이 실패하면 그 예외를 원인으로 자동 이체 상환 실패를 던진다")
        void Job이_실패하면_자동_이체_상환_실패를_던진다() throws Exception {
            // given
            AutoDebitRepaymentJobRunner runner = newRunner();
            IllegalStateException cause = new IllegalStateException("예상하지 못한 오류");
            JobExecution failed = execution(BatchStatus.FAILED);
            failed.addFailureException(cause);
            given(jobLauncher.run(eq(autoDebitRepaymentJob), any())).willReturn(failed);

            // when
            Throwable thrown = catchThrowable(runner::runManually);

            // then
            assertThat(thrown).isInstanceOf(AutoDebitRepaymentFailedException.class).hasCause(cause);
        }

        @Test
        @DisplayName("JobLauncher가 실행을 거절해 JobExecutionAlreadyRunningException을 던지면 그 예외를 원인으로 자동 이체 상환 실패를 던진다")
        void 실행이_거절되면_자동_이체_상환_실패로_감싼다() throws Exception {
            // given
            AutoDebitRepaymentJobRunner runner = newRunner();
            JobExecutionAlreadyRunningException cause = new JobExecutionAlreadyRunningException("이미 실행 중");
            given(jobLauncher.run(eq(autoDebitRepaymentJob), any())).willThrow(cause);

            // when
            Throwable thrown = catchThrowable(runner::runManually);

            // then
            assertThat(thrown).isInstanceOf(AutoDebitRepaymentFailedException.class).hasCause(cause);
        }
    }

    private AutoDebitRepaymentJobRunner newRunner() {
        return new AutoDebitRepaymentJobRunner(jobLauncher, autoDebitRepaymentJob, NINE_IN_SEOUL);
    }

    private static JobExecution execution(BatchStatus status) {
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(status);
        return execution;
    }
}
