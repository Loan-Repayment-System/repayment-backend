package com.repayment.batch.job;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.baserate.exception.BaseRateFetchFailedException;
import com.repayment.domain.baserate.service.BaseRateService;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

// Job 테스트는 @Transactional로 롤백할 수 없어 daily_closing을 테스트마다 지운다.
// 한국은행 API를 실제로 부를 수 없어 기준금리 조회는 목으로 바꾼다.
// 판정 Step은 대상을 제대로 읽어 넘기는지만 보므로 판정 서비스도 목으로 바꾼다.
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

    @MockitoBean
    private BaseRateService baseRateService;

    @MockitoBean
    private RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;

    @MockitoBean
    private LoanStatusJudgmentService loanStatusJudgmentService;

    @MockitoBean
    private LoanStatusChangeService loanStatusChangeService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM daily_closing");
        jdbcTemplate.update("DELETE FROM loan_status_judgment");
        jdbcTemplate.update("DELETE FROM loan_account");
        jdbcTemplate.update("DELETE FROM loan_contract");
        jdbcTemplate.update("DELETE FROM loan_product");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Nested
    @DisplayName("전날 10/16이 완료이고 10/17 행이 없을 때")
    class 시작할_수_있음 {

        @Test
        @DisplayName("대출이 없으면 시작·기준금리 조회·스케줄 판정·상태 판정·상태 변화 처리·완료 Step이 차례로 끝나고 10/17 행이 완료로 남는다")
        void 시작부터_완료까지_이어진다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);
            given(baseRateService.collectKoribor12m(TODAY)).willReturn(1);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(execution.getStepExecutions())
                    .extracting(StepExecution::getStepName, StepExecution::getStatus)
                    .containsExactly(
                            tuple("startClosingStep", BatchStatus.COMPLETED),
                            tuple("fetchBaseRateStep", BatchStatus.COMPLETED),
                            tuple("judgeScheduleStep", BatchStatus.COMPLETED),
                            tuple("judgeLoanStatusStep", BatchStatus.COMPLETED),
                            tuple("processStatusChangeStep", BatchStatus.COMPLETED),
                            tuple("completeClosingStep", BatchStatus.COMPLETED));
            Map<String, Object> row = findClosing(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(COMPLETED);
            assertThat(row.get("finished_at")).isNotNull();
        }

        @Test
        @DisplayName("대출 2개 중 하나가 최근 판정 완제면 완제 아닌 대출만 10/17로 스케줄 판정·상태 판정을 1번씩 하고 Job이 완료된다")
        void 완제_아닌_대출만_기준일로_판정한다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);
            long target = insertLoanAccount("900-000-000001");
            long paidOff = insertLoanAccount("900-000-000002");
            jdbcTemplate.update("INSERT INTO loan_status_judgment (loan_account_id, business_date, "
                    + "loan_judgment_status_id) VALUES (?, '2026-10-16', 2)", paidOff);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            then(repaymentScheduleJudgmentService).should(times(1)).judgeLoan(target, TODAY);
            then(repaymentScheduleJudgmentService).should(times(1)).judgeLoan(anyLong(), any());
            then(loanStatusJudgmentService).should(times(1)).judge(target, TODAY);
            then(loanStatusJudgmentService).should(times(1)).judge(anyLong(), any());
        }

        // 판정 서비스가 목이라 판정 행이 생기지 않으므로, 오늘 판정 행을 미리 넣어 다시 실행하는 상황을 만든다
        @Test
        @DisplayName("대출에 10/17 판정이 이미 있으면 상태 판정은 건너뛰고 스케줄 판정과 상태 변화 처리만 10/17로 1번씩 한다")
        void 다시_실행하면_상태_판정은_건너뛰고_상태_변화를_처리한다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);
            long judgedToday = insertLoanAccount("900-000-000001");
            jdbcTemplate.update("INSERT INTO loan_status_judgment (loan_account_id, business_date, "
                    + "loan_judgment_status_id) VALUES (?, '2026-10-17', 21)", judgedToday);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            then(repaymentScheduleJudgmentService).should(times(1)).judgeLoan(judgedToday, TODAY);
            then(loanStatusJudgmentService).shouldHaveNoInteractions();
            then(loanStatusChangeService).should(times(1)).process(judgedToday, TODAY);
            then(loanStatusChangeService).should(times(1)).process(anyLong(), any());
        }

        @Test
        @DisplayName("기준금리 조회가 실패하면 Job이 실패하고 완료 Step은 실행하지 않으며 10/17 행을 실패로 바꾼다")
        void 시작한_뒤_실패하면_실패로_바꾼다() throws Exception {
            // given
            insertClosing(YESTERDAY, COMPLETED);
            given(baseRateService.collectKoribor12m(TODAY))
                    .willThrow(new BaseRateFetchFailedException("한국은행 API 오류 응답 [INFO-100]"));

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(TODAY));

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(execution.getStepExecutions())
                    .extracting(StepExecution::getStepName)
                    .containsExactly("startClosingStep", "fetchBaseRateStep");
            Map<String, Object> row = findClosing(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(FAILED);
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

    // loan_account는 계약당 1개라 대출마다 회원·상품·계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES (?, 'pw', '테스트')", accountNo);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 3, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_bank_code, repayment_account_no, "
                + "account_no, loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, '999', '110-000-000001', ?, 1, 10000000, 10, 0)", contractId, accountNo);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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
