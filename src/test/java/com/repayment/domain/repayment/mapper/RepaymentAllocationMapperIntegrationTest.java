package com.repayment.domain.repayment.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.dto.response.RepaymentAllocationResponse;
import com.repayment.global.code.RepaymentTargetType;
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
@DisplayName("충당 상세 매퍼")
class RepaymentAllocationMapperIntegrationTest {

    @Autowired
    private RepaymentAllocationMapper repaymentAllocationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long repaymentId;

    // 상환 1건(600,000원)
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('allocation-test', 'pw', '테스트')");
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        long loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_bank_code, repayment_account_no, "
                + "account_no, loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, '999', '110-000-000001', '900-000-000201', 2, 5000000, 10, 3)", contractId);
        repaymentId = insert("INSERT INTO repayment (loan_account_id, repayment_channel_id, request_key, paid_amount, "
                + "paid_at, transaction_no) VALUES (?, 1, 'allocation-test-key', 600000, NOW(), 'TX-ALLOCATION-1')",
                loanAccountId);
    }

    @Nested
    @DisplayName("충당 상세를 기록할 때")
    class 충당_상세_기록 {

        @Test
        @DisplayName("(상환, 회차 연체금, 101, 300원)을 넣으면 종류 id 1, 대상 101, 충당 금액 300원으로 1행이 생긴다")
        void 넣은_값_그대로_1행이_생긴다() {
            // when
            repaymentAllocationMapper.insertAllocation(RepaymentAllocationResponse.builder()
                    .repaymentId(repaymentId)
                    .repaymentTargetTypeId(RepaymentTargetType.INSTALLMENT_DELINQUENCY_CHARGE)
                    .targetId(101L)
                    .paidAmount(new BigDecimal("300"))
                    .build());

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT repayment_target_type_id, target_id, "
                    + "paid_amount FROM repayment_allocation WHERE repayment_id = ?", repaymentId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(((Number) row.get("repayment_target_type_id")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(101L);
            assertThat((BigDecimal) row.get("paid_amount")).isEqualByComparingTo("300");
        }
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
