package com.repayment.batch.reader;

import com.repayment.TestcontainersConfig;
import com.repayment.global.code.AutoDebitAgreementStatus;
import com.repayment.global.config.TransientDatabaseRetryConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자동 이체 상환 Reader가 페이지 조회 실패를 어떻게 다시 시도하는지 검증한다.
 * Reader는 @StepScope라 설정 메서드로 직접 만들고, 커넥션 획득에 실패하는 DataSource를 넘긴다.
 * Reader가 테스트와 다른 커넥션으로 읽으므로 @Transactional로 넣은 행을 볼 수 없다. 커밋하고 테스트마다 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("자동 이체 상환 Reader")
class AutoDebitRepaymentReaderConfigIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ConnectionFailingDataSource failingDataSource;

    @BeforeEach
    void setUp() {
        failingDataSource = new ConnectionFailingDataSource(dataSource);
        jdbcTemplate.update("INSERT INTO member (member_id, login_id, password, name) "
                + "VALUES (1, 'reader-test', 'pw', '테스트')");
        jdbcTemplate.update("INSERT INTO loan_product (loan_product_id, interest_type_id, repayment_method_id, name, "
                + "additional_rate) VALUES (1, 1, 2, '테스트 상품', 2.00000)");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM auto_debit_agreement");
        jdbcTemplate.update("DELETE FROM loan_account");
        jdbcTemplate.update("DELETE FROM loan_contract");
        jdbcTemplate.update("DELETE FROM loan_product");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Nested
    @DisplayName("커넥션 획득이 상한 3번 안에서 실패했다가 성공할 때")
    class 상한_안에서_실패 {

        // 실패 대처 case 1
        @Test
        @DisplayName("대상 3건의 첫 페이지 조회가 1번 실패하면 다시 조회해 3건을 모두 읽는다")
        void 첫_페이지를_다시_조회한다() throws Exception {
            // given
            insertTargets(3);
            RetryingJdbcPagingItemReader<Long> reader = openTargetReader();
            failingDataSource.failNextConnections(1);

            // when
            List<Long> result = readRest(reader);

            // then
            assertThat(result).containsExactly(1L, 2L, 3L);
        }

        // 실패 대처 case 1
        @Test
        @DisplayName("대상 150건의 두 번째 페이지 조회가 1번 실패하면 150건을 빠짐과 중복 없이 순서대로 읽는다")
        void 두_번째_페이지를_다시_조회한다() throws Exception {
            // given
            insertTargets(150);
            RetryingJdbcPagingItemReader<Long> reader = openTargetReader();
            List<Long> firstPage = read(reader, 100);
            failingDataSource.failNextConnections(1);

            // when
            List<Long> rest = readRest(reader);

            // then
            List<Long> result = new ArrayList<>(firstPage);
            result.addAll(rest);
            assertThat(result).containsExactlyElementsOf(LongStream.rangeClosed(1, 150).boxed().toList());
        }

        // 실패 대처 case 1
        @Test
        @DisplayName("첫 페이지 조회가 2번 실패한 뒤 성공하면 커넥션을 3번 시도해 모두 읽는다")
        void 세_번째_시도에서_성공한다() throws Exception {
            // given
            insertTargets(3);
            RetryingJdbcPagingItemReader<Long> reader = openTargetReader();
            failingDataSource.failNextConnections(2);

            // when
            List<Long> result = readRest(reader);

            // then
            assertThat(result).containsExactly(1L, 2L, 3L);
            assertThat(failingDataSource.getConnectionAttempts()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("페이지 조회가 끝내 실패할 때")
    class 끝내_실패 {

        // 실패 대처 case 1
        @Test
        @DisplayName("첫 페이지 조회가 3번 연속 실패하면 커넥션을 3번 시도한 뒤 커넥션 획득 실패 예외를 던진다")
        void 상한을_넘으면_예외를_던진다() throws Exception {
            // given
            insertTargets(3);
            RetryingJdbcPagingItemReader<Long> reader = openTargetReader();
            failingDataSource.failNextConnections(3);

            // when, then
            assertThatThrownBy(reader::read).isInstanceOf(CannotGetJdbcConnectionException.class);
            assertThat(failingDataSource.getConnectionAttempts()).isEqualTo(3);
        }

        // 실패 대처 case 1
        @Test
        @DisplayName("없는 테이블을 조회하면 다시 시도하지 않고 커넥션 1번 만에 SQL 문법 오류 예외를 던진다")
        void SQL_문법_오류는_다시_시도하지_않는다() throws Exception {
            // given
            RetryingJdbcPagingItemReader<Long> reader = openReader("SELECT id", "FROM no_such_table", "id");

            // when, then
            assertThatThrownBy(reader::read).isInstanceOf(BadSqlGrammarException.class);
            assertThat(failingDataSource.getConnectionAttempts()).isEqualTo(1);
        }

        // 실패 대처 case 1
        @Test
        @DisplayName("MAX_EXECUTION_TIME을 넘겨 DB가 쿼리를 중단하면 커넥션을 3번 시도한 뒤 오류 코드 3024 예외를 던진다")
        void 쿼리_시간_초과는_다시_시도한다() throws Exception {
            // given
            RetryingJdbcPagingItemReader<Long> reader = openReader(
                    "SELECT /*+ MAX_EXECUTION_TIME(1) */ a.ORDINAL_POSITION",
                    "FROM information_schema.columns a, information_schema.columns b, information_schema.columns c",
                    "ORDINAL_POSITION");

            // when, then
            assertThatThrownBy(reader::read)
                    .isInstanceOfSatisfying(UncategorizedSQLException.class,
                            e -> assertThat(e.getSQLException().getErrorCode()).isEqualTo(3024));
            assertThat(failingDataSource.getConnectionAttempts()).isEqualTo(3);
        }
    }

    // 대기 시간은 검증 대상이 아니라 대기 없이 돌린다. 재시도 횟수와 대상 예외는 설정 그대로 쓴다.
    private RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new TransientDatabaseRetryConfig().transientDatabaseRetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    private RetryingJdbcPagingItemReader<Long> openTargetReader() throws Exception {
        RetryingJdbcPagingItemReader<Long> reader = new AutoDebitRepaymentReaderConfig()
                .autoDebitRepaymentTargetReader(failingDataSource, retryTemplate(), TODAY);
        return open(reader);
    }

    private RetryingJdbcPagingItemReader<Long> openReader(String selectClause, String fromClause, String sortKey)
            throws Exception {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause(selectClause);
        queryProvider.setFromClause(fromClause);
        queryProvider.setSortKeys(Map.of(sortKey, Order.ASCENDING));
        RetryingJdbcPagingItemReader<Long> reader = new RetryingJdbcPagingItemReader<>(retryTemplate());
        reader.setName("testReader");
        reader.setDataSource(failingDataSource);
        reader.setPageSize(100);
        reader.setQueryProvider(queryProvider);
        reader.setRowMapper(new SingleColumnRowMapper<>(Long.class));
        return open(reader);
    }

    private RetryingJdbcPagingItemReader<Long> open(RetryingJdbcPagingItemReader<Long> reader) throws Exception {
        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());
        return reader;
    }

    private List<Long> read(RetryingJdbcPagingItemReader<Long> reader, int count) throws Exception {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(reader.read());
        }
        return result;
    }

    private List<Long> readRest(RetryingJdbcPagingItemReader<Long> reader) throws Exception {
        List<Long> result = new ArrayList<>();
        for (Long id = reader.read(); id != null; id = reader.read()) {
            result.add(id);
        }
        reader.close();
        return result;
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다. ID는 1부터 차례로 붙인다.
    private void insertTargets(int count) {
        for (long id = 1; id <= count; id++) {
            jdbcTemplate.update("INSERT INTO loan_contract (loan_contract_id, loan_product_id, member_id, "
                    + "contract_date, maturity_date, contract_principal, contract_additional_rate, "
                    + "contract_base_rate, total_installments) "
                    + "VALUES (?, 1, 1, '2026-09-17', '2027-09-17', 12000000, 2.00000, 3.00000, 12)", id);
            jdbcTemplate.update("INSERT INTO loan_account (loan_account_id, loan_contract_id, repayment_bank_code, "
                    + "repayment_account_no, account_no, loan_status_id, loan_principal_balance, monthly_payment_day) "
                    + "VALUES (?, ?, '999', '200-000-000001', ?, 1, 12000000, 17)",
                    id, id, String.format("900-000-%06d", id));
            jdbcTemplate.update("INSERT INTO auto_debit_agreement (loan_account_id, auto_debit_agreement_status_id, "
                    + "start_date) VALUES (?, ?, '2026-10-01')", id, AutoDebitAgreementStatus.ACTIVE.getId());
        }
    }
}
