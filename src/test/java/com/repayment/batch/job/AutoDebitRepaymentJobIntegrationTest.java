package com.repayment.batch.job;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.global.code.AutoDebitAgreementStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 약정·판정에 따라 어떤 대출을 자동 상환으로 상환하는지 검증한다.
 * 펌뱅킹 클라이언트는 목으로 바꾸고, 집금 요청이 클라이언트까지 왔으면 펌뱅킹은 이체를 끝낸 것으로 본다.
 * 상환이 트랜잭션을 스스로 커밋하므로 @Transactional로 롤백할 수 없다. 테스트마다 행을 지운다.
 */
@SpringBatchTest
@SpringBootTest
@Import({TestcontainersConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("자동 이체 상환 Job")
class AutoDebitRepaymentJobIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfig.NOW.toLocalDate();
    private static final String SUCCESS = "0000";
    private static final String INSUFFICIENT_BALANCE = "B003";
    // 1회차 원금 977,289 + 이자 50,000
    private static final BigDecimal FIRST_INSTALLMENT = new BigDecimal("1027289");
    private static final int COMPLETED = 3;
    private static final int FAILED = 4;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job autoDebitRepaymentJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirmbankingClient firmbankingClient;

    @BeforeEach
    void setUp() {
        // Job이 여러 개라 JobLauncherTestUtils가 실행할 Job을 스스로 고르지 못한다.
        jobLauncherTestUtils.setJob(autoDebitRepaymentJob);
        jdbcTemplate.update("INSERT INTO member (member_id, login_id, password, name) "
                + "VALUES (1, 'kimcs', 'password', '김철수')");
        jdbcTemplate.update("INSERT INTO loan_product (loan_product_id, interest_type_id, repayment_method_id, name, "
                + "additional_rate) VALUES (1, 1, 2, '고정금리 원리금균등', 2.00000)");
        jdbcTemplate.update("INSERT INTO daily_closing (business_date, daily_closing_status_id, started_at, "
                + "finished_at) VALUES ('2026-10-17', 2, '2026-10-17 00:00:00', '2026-10-17 00:01:00')");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM repayment_allocation");
        jdbcTemplate.update("DELETE FROM repayment");
        jdbcTemplate.update("DELETE FROM collection_request");
        jdbcTemplate.update("DELETE FROM loan_status_judgment");
        jdbcTemplate.update("DELETE FROM auto_debit_agreement");
        jdbcTemplate.update("DELETE FROM repayment_schedule");
        jdbcTemplate.update("DELETE FROM loan_account");
        jdbcTemplate.update("DELETE FROM loan_contract");
        jdbcTemplate.update("DELETE FROM loan_product");
        jdbcTemplate.update("DELETE FROM member");
        jdbcTemplate.update("DELETE FROM daily_closing");
        jdbcTemplate.update("DELETE FROM telegram_no_sequence");
    }

    @Nested
    @DisplayName("약정이 사용 중이고 10/17이 약정 기간 안이며 1회차 1,027,289원이 납부 기한 도래일 때")
    class 대상_대출 {

        @Test
        @DisplayName("Job을 실행하면 자동 상환 집금 요청 1건이 상환 완료로 남고 납부 기록 1건이 생긴다")
        void 갚을_금액_전액을_자동_상환으로_상환한다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.ACTIVE, "2026-10-01", null);
            givenResponseCode(SUCCESS);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(jdbcTemplate.queryForList("SELECT repayment_channel_id FROM collection_request", Integer.class))
                    .containsExactly(RepaymentChannel.AUTO.getId());
            assertThat(findRequestStatusIds()).containsExactly(COMPLETED);
            assertThat(jdbcTemplate.queryForObject("SELECT amount FROM collection_request", BigDecimal.class))
                    .isEqualByComparingTo(FIRST_INSTALLMENT);
            assertThat(countRepayments()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("1회차가 납부 기한 도래이지만 대상이 아닐 때")
    class 대상_아님 {

        @Test
        @DisplayName("약정이 정지면 집금 요청을 보내지 않는다")
        void 약정이_정지면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.SUSPENDED, "2026-10-01", null);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }

        @Test
        @DisplayName("약정이 해지면 집금 요청을 보내지 않는다")
        void 약정이_해지면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.TERMINATED, "2026-10-01", null);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }

        @Test
        @DisplayName("약정 시작일이 10/17 뒤인 10/18이면 집금 요청을 보내지 않는다")
        void 약정_시작_전이면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.ACTIVE, "2026-10-18", null);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }

        @Test
        @DisplayName("약정 종료일이 10/17 전인 10/16이면 집금 요청을 보내지 않는다")
        void 약정_종료_뒤면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.ACTIVE, "2026-10-01", "2026-10-16");

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }

        @Test
        @DisplayName("약정이 없으면 집금 요청을 보내지 않는다")
        void 약정이_없으면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }

        @Test
        @DisplayName("약정이 사용 중이어도 가장 최근 판정이 완제면 집금 요청을 보내지 않는다")
        void 완제면_상환하지_않는다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertAgreement(1L, AutoDebitAgreementStatus.ACTIVE, "2026-10-01", null);
            jdbcTemplate.update("INSERT INTO loan_status_judgment (loan_account_id, business_date, "
                    + "loan_judgment_status_id) VALUES (1, '2026-10-16', ?)", LoanJudgmentStatus.PAID_OFF.getId());

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countCollectionRequests()).isZero();
        }
    }

    @Nested
    @DisplayName("대상 대출 2건 중 1건의 상환 계좌 잔액이 모자랄 때")
    class 일부_거절 {

        // 요구사항 9: 한 대출이 실패해도 나머지 대출은 계속 처리한다
        @Test
        @DisplayName("대출 1이 잔액 부족으로 거절되어도 Job은 완료되고 대출 2는 상환 완료로 남는다")
        void 거절된_대출을_넘기고_나머지를_상환한다() throws Exception {
            // given
            insertLoanWithFirstInstallmentDue(1L, "200-000-000001");
            insertLoanWithFirstInstallmentDue(2L, "200-000-000002");
            insertAgreement(1L, AutoDebitAgreementStatus.ACTIVE, "2026-10-01", null);
            insertAgreement(2L, AutoDebitAgreementStatus.ACTIVE, "2026-10-01", null);
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), eq("200-000-000001"), any()))
                    .willReturn(INSUFFICIENT_BALANCE);
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), eq("200-000-000002"), any()))
                    .willReturn(SUCCESS);

            // when
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

            // then
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(jdbcTemplate.queryForList("SELECT collection_request_status_id FROM collection_request "
                    + "ORDER BY loan_account_id", Integer.class))
                    .containsExactly(FAILED, COMPLETED);
            assertThat(countRepayments()).isEqualTo(1);
        }
    }

    private JobParameters jobParameters() {
        return new JobParametersBuilder()
                .addLocalDate("businessDate", TODAY)
                .addLocalDateTime("requestedAt", LocalDateTime.now())
                .toJobParameters();
    }

    private void givenResponseCode(String responseCode) {
        given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                .willReturn(responseCode);
    }

    private void insertAgreement(long loanAccountId, AutoDebitAgreementStatus status, String startDate,
                                 String endDate) {
        jdbcTemplate.update("INSERT INTO auto_debit_agreement (loan_account_id, auto_debit_agreement_status_id, "
                + "start_date, end_date) VALUES (?, ?, ?, ?)", loanAccountId, status.getId(), startDate, endDate);
    }

    // 2026-09-17 계약, 원금 12,000,000원, 12회 원리금균등. 1회차(10/17)만 확정되어 납부 기한 도래다.
    private void insertLoanWithFirstInstallmentDue(long loanAccountId, String repaymentAccountNo) {
        jdbcTemplate.update("INSERT INTO loan_contract (loan_contract_id, loan_product_id, member_id, contract_date, "
                + "maturity_date, contract_principal, contract_additional_rate, contract_base_rate, "
                + "total_installments) VALUES (?, 1, 1, '2026-09-17', '2027-09-17', 12000000, 2.00000, 3.00000, 12)",
                loanAccountId);
        jdbcTemplate.update("INSERT INTO loan_account (loan_account_id, loan_contract_id, repayment_bank_code, "
                + "repayment_account_no, account_no, loan_status_id, loan_principal_balance, monthly_payment_day) "
                + "VALUES (?, ?, '999', ?, ?, 1, 12000000, 17)",
                loanAccountId, loanAccountId, repaymentAccountNo, "900-000-00000" + loanAccountId);
        jdbcTemplate.update("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date, scheduled_principal, scheduled_interest, "
                + "applied_base_rate, applied_additional_rate, confirmed_at) "
                + "VALUES (?, 1, 2, '2026-10-17', 977289, 50000, 3.00000, 2.00000, '2026-10-17 00:00:30')",
                loanAccountId);
        jdbcTemplate.update("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date) "
                + "WITH RECURSIVE installment (installment_no) AS ("
                + "    SELECT 2 UNION ALL SELECT installment_no + 1 FROM installment WHERE installment_no < 12) "
                + "SELECT ?, installment_no, 1, DATE_ADD('2026-09-17', INTERVAL installment_no MONTH) "
                + "FROM installment", loanAccountId);
    }

    private List<Integer> findRequestStatusIds() {
        return jdbcTemplate.queryForList("SELECT collection_request_status_id FROM collection_request", Integer.class);
    }

    private int countCollectionRequests() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM collection_request", Integer.class);
    }

    private int countRepayments() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM repayment", Integer.class);
    }
}
