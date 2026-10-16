package com.repayment.domain.maturity.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeResponse;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeTargetResponse;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("만기 경과 연체금 매퍼")
class MaturityOverdueDailyChargeMapperIntegrationTest {

    private static final LocalDate CHARGE_DATE = LocalDate.of(2027, 1, 12);

    @Autowired
    private MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long accountId;
    private long productId;
    private long loanAccountId;

    // 원리금균등, 약정 3,000,000원, 만기 2027-01-10, 원금잔액 2,002,000원
    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('maturity-charge-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000006', 0, 1)", memberId);
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (2, 2, '테스트 상품', 1.5)");
        loanAccountId = insertLoanAccount("900-000-000009");
    }

    @Nested
    @DisplayName("계산할 값을 조회할 때")
    class 대상_조회 {

        @Test
        @DisplayName("전환 기록의 기준 5.0%·가산 1.5%와 원금잔액 2,002,000원을 가져온다")
        void 전환_기록과_원금잔액을_가져온다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);

            // when
            Optional<MaturityOverdueDailyChargeTargetResponse> result =
                    maturityOverdueDailyChargeMapper.findAccrualTarget(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(target -> {
                assertThat(target.getLoanMaturityOverdueId()).isEqualTo(maturityOverdueId);
                assertThat(target.getAppliedBaseRate()).isEqualByComparingTo("5.0");
                assertThat(target.getAppliedAdditionalRate()).isEqualByComparingTo("1.5");
                assertThat(target.getLoanPrincipalBalance()).isEqualByComparingTo("2002000");
            });
        }

        @Test
        @DisplayName("다른 대출만 만기 경과면 비어 있다")
        void 다른_대출만_전환했으면_비어_있다() {
            // given
            insertMaturityOverdue(insertLoanAccount("900-000-000010"));

            // when
            Optional<MaturityOverdueDailyChargeTargetResponse> result =
                    maturityOverdueDailyChargeMapper.findAccrualTarget(loanAccountId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("그날 쌓았는지 조회할 때")
    class 그날_행_조회 {

        @Test
        @DisplayName("1/11 행만 있으면 1/12는 false다")
        void 다른_날짜_행만_있으면_false다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            insertChargeRow(maturityOverdueId, "2027-01-11");

            // when
            boolean result = maturityOverdueDailyChargeMapper
                    .existsByLoanMaturityOverdueIdAndChargeDate(maturityOverdueId, CHARGE_DATE);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("1/12 행이 있으면 1/12는 true다")
        void 그날_행이_있으면_true다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            insertChargeRow(maturityOverdueId, "2027-01-12");

            // when
            boolean result = maturityOverdueDailyChargeMapper
                    .existsByLoanMaturityOverdueIdAndChargeDate(maturityOverdueId, CHARGE_DATE);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("연체금을 기록할 때")
    class 연체금_기록 {

        @Test
        @DisplayName("(1/12, 근거 1, 대상 2,002,000원, 9%, 493원, 진행중)을 넣으면 그 값 그대로 1행이 생기고 약정이자·낸 금액은 모두 0원이다")
        void 넘긴_값_그대로_1행을_넣는다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            MaturityOverdueDailyChargeResponse charge = MaturityOverdueDailyChargeResponse.builder()
                    .loanMaturityOverdueId(maturityOverdueId)
                    .chargeDate(CHARGE_DATE)
                    .loanJudgmentStatusId(LoanJudgmentStatus.MATURITY_OVERDUE)
                    .delinquencyTargetAmount(new BigDecimal("2002000"))
                    .delinquencyRate(new BigDecimal("9"))
                    .delinquencyCharge(new BigDecimal("493"))
                    .delinquencyStatusId(DelinquencyStatus.IN_PROGRESS)
                    .build();

            // when
            maturityOverdueDailyChargeMapper.insertCharge(charge);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT charge_date, loan_judgment_status_id, delinquency_target_amount, delinquency_rate, "
                            + "delinquency_charge, paid_delinquency_charge, interest_amount, paid_interest, "
                            + "delinquency_status_id "
                            + "FROM maturity_overdue_daily_charge WHERE loan_maturity_overdue_id = ?", maturityOverdueId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("charge_date").toString()).isEqualTo("2027-01-12");
            assertThat(((Number) row.get("loan_judgment_status_id")).intValue()).isEqualTo(1);
            assertThat((BigDecimal) row.get("delinquency_target_amount")).isEqualByComparingTo("2002000");
            assertThat((BigDecimal) row.get("delinquency_rate")).isEqualByComparingTo("9");
            assertThat((BigDecimal) row.get("delinquency_charge")).isEqualByComparingTo("493");
            assertThat(((Number) row.get("delinquency_status_id")).intValue()).isEqualTo(1);
            assertThat((BigDecimal) row.get("interest_amount")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_delinquency_charge")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_interest")).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("충당한 금액을 더할 때")
    class 충당한_금액_더하기 {

        @Test
        @DisplayName("낸 연체금 100원인 1/12 행에 연체금 200원을 더하면 300원이 되고 1/13 행은 0원 그대로다")
        void 그_하루_기록에만_더한다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            insertChargeRow(maturityOverdueId, "2027-01-12");
            long jan12ChargeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            insertChargeRow(maturityOverdueId, "2027-01-13");
            long jan13ChargeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbcTemplate.update("UPDATE maturity_overdue_daily_charge SET paid_delinquency_charge = 100 "
                    + "WHERE maturity_overdue_daily_charge_id = ?", jan12ChargeId);

            // when
            maturityOverdueDailyChargeMapper.updateAddPaidDelinquencyCharge(jan12ChargeId, new BigDecimal("200"));

            // then
            assertThat(findPaidDelinquencyCharge(jan12ChargeId)).isEqualByComparingTo("300");
            assertThat(findPaidDelinquencyCharge(jan13ChargeId)).isEqualByComparingTo("0");
        }

        private BigDecimal findPaidDelinquencyCharge(long maturityOverdueDailyChargeId) {
            return jdbcTemplate.queryForObject("SELECT paid_delinquency_charge FROM maturity_overdue_daily_charge "
                    + "WHERE maturity_overdue_daily_charge_id = ?", BigDecimal.class, maturityOverdueDailyChargeId);
        }
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-10-10', '2027-01-10', 3000000, 1.5, 4.5, 3)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, ?, 5, 2002000, 10, 3)", contractId, accountId, accountNo);
    }

    private long insertMaturityOverdue(long loanAccountId) {
        return insert("INSERT INTO loan_maturity_overdue (loan_account_id, converted_date, applied_base_rate, "
                + "applied_additional_rate, carried_interest, carried_delinquency_charge) "
                + "VALUES (?, '2027-01-11', 5.0, 1.5, 78000, 500)", loanAccountId);
    }

    private void insertChargeRow(long maturityOverdueId, String chargeDate) {
        insert("INSERT INTO maturity_overdue_daily_charge (loan_maturity_overdue_id, charge_date, loan_judgment_status_id, "
                + "delinquency_target_amount, delinquency_rate, delinquency_charge, delinquency_status_id) "
                + "VALUES (?, ?, 1, 2002000, 9, 493, 1)", maturityOverdueId, chargeDate);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
