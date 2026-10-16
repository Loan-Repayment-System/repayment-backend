package com.repayment.domain.repayment.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("상환 매퍼")
class RepaymentMapperIntegrationTest {

    @Autowired
    private RepaymentMapper repaymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanAccountId;

    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('repayment-test', 'pw', '테스트')");
        long accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000011', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, "
                + "loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000301', 1, 10000000, 10, 10)", contractId, accountId);
    }

    @Nested
    @DisplayName("상환을 기록할 때")
    class 상환_기록 {

        @Test
        @DisplayName("(대출, 고객 상환, req-1, 30,000원, 2026-10-16 14:30, 거래번호)를 넣으면 그 값 그대로 1행이 생기고 만든 id가 DTO에 채워진다")
        void 넘긴_값_그대로_1행을_넣고_id를_채운다() {
            // given
            RepaymentResponse repayment = RepaymentResponse.builder()
                    .loanAccountId(loanAccountId)
                    .repaymentChannelId(RepaymentChannel.CUSTOMER)
                    .requestKey("req-1")
                    .paidAmount(new BigDecimal("30000"))
                    .paidAt(LocalDateTime.of(2026, 10, 16, 14, 30))
                    .transactionNo("3f2b9c1e-0000-4000-8000-000000000001")
                    .build();

            // when
            repaymentMapper.insertRepayment(repayment);

            // then
            assertThat(repayment.getRepaymentId()).isNotNull();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT repayment_id, repayment_channel_id, request_key, paid_amount, paid_at, transaction_no "
                            + "FROM repayment WHERE loan_account_id = ?", loanAccountId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(((Number) row.get("repayment_id")).longValue()).isEqualTo(repayment.getRepaymentId());
            assertThat(((Number) row.get("repayment_channel_id")).intValue()).isEqualTo(1);
            assertThat(row.get("request_key")).isEqualTo("req-1");
            assertThat((BigDecimal) row.get("paid_amount")).isEqualByComparingTo("30000");
            assertThat(row.get("paid_at")).isEqualTo(LocalDateTime.of(2026, 10, 16, 14, 30));
            assertThat(row.get("transaction_no")).isEqualTo("3f2b9c1e-0000-4000-8000-000000000001");
        }
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
