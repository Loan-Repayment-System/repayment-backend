package com.repayment.batch.reader;

import com.repayment.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Reader는 @StepScope라 Step 밖에서는 빈으로 꺼낼 수 없어, 설정 메서드로 직접 만들어 같은 DB에서 읽는다.
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("일마감 Reader")
class DailyClosingReaderConfigIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long accountId;
    private long productId;

    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('reader-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000001', 0, 1)", memberId);
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 3, '테스트 상품', 1.5)");
    }

    @Nested
    @DisplayName("스케줄 판정 대상을 읽을 때")
    class 스케줄_판정_대상 {

        @Test
        @DisplayName("A 판정 없음, B 최근 0 정상, C 0 정상 → 2 완제, D 최근 21 연체면 완제인 C만 빼고 A·B·D 순서로 읽는다")
        void 최근_판정이_완제인_대출만_뺀다() throws Exception {
            // given
            long noJudgment = insertLoanAccount(1);
            long normal = insertLoanAccount(2);
            insertJudgment(normal, "2026-10-16", 0);
            long paidOff = insertLoanAccount(3);
            insertJudgment(paidOff, "2026-10-15", 0);
            insertJudgment(paidOff, "2026-10-16", 2);
            long overdue = insertLoanAccount(4);
            insertJudgment(overdue, "2026-10-16", 21);

            // when
            List<Long> result = readAll(new DailyClosingReaderConfig().scheduleJudgmentTargetReader(dataSource));

            // then
            assertThat(result).containsExactly(noJudgment, normal, overdue);
        }

        @Test
        @DisplayName("판정 없는 대출이 101개면 페이지 크기 100을 넘어 101개를 빠짐없이 ID 순서로 읽는다")
        void 페이지를_넘어_이어_읽는다() throws Exception {
            // given
            List<Long> loanAccountIds = new ArrayList<>();
            for (int i = 1; i <= 101; i++) {
                loanAccountIds.add(insertLoanAccount(i));
            }

            // when
            List<Long> result = readAll(new DailyClosingReaderConfig().scheduleJudgmentTargetReader(dataSource));

            // then
            assertThat(result).containsExactlyElementsOf(loanAccountIds);
        }
    }

    private List<Long> readAll(JdbcPagingItemReader<Long> reader) throws Exception {
        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());
        List<Long> result = new ArrayList<>();
        for (Long id = reader.read(); id != null; id = reader.read()) {
            result.add(id);
        }
        reader.close();
        return result;
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(int no) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, ?, 1, 10000000, 10, 0)", contractId, accountId, String.format("900-000-%06d", no));
    }

    private void insertJudgment(long loanAccountId, String businessDate, int loanJudgmentStatusId) {
        insert("INSERT INTO loan_status_judgment (loan_account_id, business_date, loan_judgment_status_id) "
                + "VALUES (?, ?, ?)", loanAccountId, businessDate, loanJudgmentStatusId);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
