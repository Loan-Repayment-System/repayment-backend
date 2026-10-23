package com.repayment.batch.job;

import com.repayment.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// Job 테스트는 @Transactional로 롤백할 수 없어 daily_closing을 테스트마다 지운다.
@SpringBatchTest
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("일마감 Job 흐름")
class DailyClosingJobIntegrationTest {

    // daily_closing_status
    private static final int IN_PROGRESS = 1;
    private static final int COMPLETED = 2;
    private static final int FAILED = 3;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 10, 16, 23, 0);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM daily_closing");
    }

    @Nested
    @DisplayName("전날 10/16이 완료이고 10/17 행이 없을 때")
    class 시작할_수_있음 {

        @Test
        @DisplayName("10/17로 실행하면 시작·완료 Step이 차례로 끝나고 10/17 행이 완료로 남는다")
        void 시작부터_완료까지_이어진다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(execution.getStepExecutions())
                    .extracting(StepExecution::getStepName, StepExecution::getStatus)
                    .containsExactly(
                            tuple("startClosingStep", BatchStatus.COMPLETED),
                            tuple("completeClosingStep", BatchStatus.COMPLETED));
            Map<String, Object> row = findClosing(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(COMPLETED);
            assertThat(row.get("finished_at")).isNotNull();
        }
    }

    @Nested
    @DisplayName("일마감 시작이 거절될 때")
    class 시작_거절 {

        @Test
        @DisplayName("전날 10/16이 실패면 Job이 실패하고 완료 Step은 실행하지 않으며 10/17 행을 만들지 않는다")
        void 시작_Step이_실패하면_뒤_Step을_실행하지_않는다() throws Exception {
            // given
            insertClosing(YESTERDAY, FAILED);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(execution.getStepExecutions())
                    .extracting(StepExecution::getStepName)
                    .containsExactly("startClosingStep");
            assertThat(countClosing(TODAY)).isZero();
        }

        @Test
        @DisplayName("10/17 행이 이미 진행중이면 Job이 실패해도 10/17 행을 진행중으로 둔다")
        void 시작하지_못한_실행은_진행중_행을_실패로_바꾸지_않는다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);
            insertClosing(TODAY, IN_PROGRESS);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            Map<String, Object> row = findClosing(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(IN_PROGRESS);
            assertThat(row.get("finished_at")).isNull();
        }
    }

    private JobParameters jobParameters(LocalDate businessDate) {
        return new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate)
                .addLocalDateTime("requestedAt", LocalDateTime.now())
                .toJobParameters();
    }

    private void insertClosing(LocalDate businessDate, int status) {
        LocalDateTime finishedAt = status == IN_PROGRESS ? null : EARLIER;
        jdbcTemplate.update(
                "INSERT INTO daily_closing (business_date, daily_closing_status_id, started_at, finished_at) VALUES (?, ?, ?, ?)",
                businessDate, status, EARLIER, finishedAt);
    }

    private Map<String, Object> findClosing(LocalDate businessDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT daily_closing_status_id, finished_at FROM daily_closing WHERE business_date = ?", businessDate);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private int countClosing(LocalDate businessDate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_closing WHERE business_date = ?", Integer.class, businessDate);
    }
}
