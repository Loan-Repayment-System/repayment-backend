package com.repayment.domain.repayment.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.dto.response.InstallmentRepaymentResponse;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("회차 충당 상세 매퍼")
class InstallmentRepaymentMapperIntegrationTest {

    @Autowired
    private InstallmentRepaymentMapper installmentRepaymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long repaymentId;
    private long repaymentScheduleId;

    // 상환 1건(600,000원)과 연체 2회차
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('allocation-test', 'pw', '테스트')");
        long accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000010', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        long loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, "
                + "loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000201', 2, 5000000, 10, 3)", contractId, accountId);
        repaymentScheduleId = insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date, scheduled_principal, scheduled_interest, "
                + "applied_base_rate, applied_additional_rate, confirmed_at) "
                + "VALUES (?, 2, 4, '2026-10-10', 500000, 50000, 4.5, 1.5, NOW())", loanAccountId);
        repaymentId = insert("INSERT INTO repayment (loan_account_id, repayment_channel_id, request_key, paid_amount, "
                + "paid_at, transaction_no) VALUES (?, 1, 'allocation-test-key', 600000, NOW(), 'TX-ALLOCATION-1')",
                loanAccountId);
    }

    @Nested
    @DisplayName("충당 상세를 기록할 때")
    class 충당_상세_기록 {

        @Test
        @DisplayName("(상환, 2회, 원금 500,000원, 이자 50,000원, 연체금 600원)을 넣으면 그 값 그대로 1행이 생긴다")
        void 넣은_값_그대로_1행이_생긴다() {
            // when
            installmentRepaymentMapper.insertInstallmentRepayment(InstallmentRepaymentResponse.builder()
                    .repaymentId(repaymentId)
                    .repaymentScheduleId(repaymentScheduleId)
                    .paidPrincipal(new BigDecimal("500000"))
                    .paidInterest(new BigDecimal("50000"))
                    .paidDelinquencyCharge(new BigDecimal("600"))
                    .build());

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT repayment_schedule_id, paid_principal, "
                    + "paid_interest, paid_delinquency_charge FROM installment_repayment WHERE repayment_id = ?", repaymentId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(((Number) row.get("repayment_schedule_id")).longValue()).isEqualTo(repaymentScheduleId);
            assertThat((BigDecimal) row.get("paid_principal")).isEqualByComparingTo("500000");
            assertThat((BigDecimal) row.get("paid_interest")).isEqualByComparingTo("50000");
            assertThat((BigDecimal) row.get("paid_delinquency_charge")).isEqualByComparingTo("600");
        }
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
