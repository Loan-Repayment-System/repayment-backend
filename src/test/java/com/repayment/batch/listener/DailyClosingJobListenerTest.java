package com.repayment.batch.listener;

import com.repayment.batch.step.StartClosingTasklet;
import com.repayment.domain.closing.service.DailyClosingService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("일마감 Job이 끝난 뒤 정리")
class DailyClosingJobListenerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Mock
    private DailyClosingService dailyClosingService;

    @InjectMocks
    private DailyClosingJobListener listener;

    @Nested
    @DisplayName("Job이 실패로 끝났을 때")
    class Job_실패 {

        @Test
        @DisplayName("이 실행이 10/17 일마감을 시작했으면 10/17을 실패로 바꾼다")
        void 시작한_실행이면_실패로_바꾼다() {
            // given
            JobExecution execution = jobExecution(BatchStatus.FAILED, true);

            // when
            listener.afterJob(execution);

            // then
            then(dailyClosingService).should().fail(TODAY);
        }

        @Test
        @DisplayName("시작 Step에서 거절돼 시작하지 못했으면 아무것도 바꾸지 않는다")
        void 시작하지_못한_실행이면_바꾸지_않는다() {
            // given
            JobExecution execution = jobExecution(BatchStatus.FAILED, false);

            // when
            listener.afterJob(execution);

            // then
            then(dailyClosingService).should(never()).fail(any());
        }
    }

    @Nested
    @DisplayName("Job이 완료로 끝났을 때")
    class Job_완료 {

        @Test
        @DisplayName("10/17 일마감을 시작했어도 실패로 바꾸지 않는다")
        void 완료된_실행은_바꾸지_않는다() {
            // given
            JobExecution execution = jobExecution(BatchStatus.COMPLETED, true);

            // when
            listener.afterJob(execution);

            // then
            then(dailyClosingService).should(never()).fail(any());
        }
    }

    private JobExecution jobExecution(BatchStatus status, boolean closingStarted) {
        JobExecution execution = new JobExecution(new JobInstance(1L, "dailyClosingJob"),
                new JobParametersBuilder().addLocalDate("businessDate", TODAY).toJobParameters());
        execution.setStatus(status);
        if (closingStarted) {
            execution.getExecutionContext().put(StartClosingTasklet.CLOSING_STARTED, true);
        }
        return execution;
    }
}
