package com.repayment.domain.acceleration.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeResponse;
import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeTargetResponse;
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
@DisplayName("기한이익상실 연체금 매퍼")
class AccelerationDailyChargeMapperIntegrationTest {

    private static final LocalDate CHARGE_DATE = LocalDate.of(2026, 10, 15);

    @Autowired
    private AccelerationDailyChargeMapper accelerationDailyChargeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanAccountId;

    // 분할상환, 약정 12,000,000원, 원금잔액 10,000,000원
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('daily-charge-test', 'pw', '테스트')");
        long accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000005', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (2, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-07-10', '2027-07-10', 12000000, 1.5, 4.5, 12)", productId, memberId);
        loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, "
                + "loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000007', 4, 10000000, 10, 3)", contractId, accountId);
    }

    @Nested
    @DisplayName("쌓을 대상을 조회할 때")
    class 대상_조회 {

        @Test
        @DisplayName("전환 기록의 금리 4.5%·1.5%, 옮긴 이자 78,000원·낸 8,000원, 옮긴 밀린 원금 1,502,000원·낸 2,000원과 원금잔액 10,000,000원을 가져온다")
        void 전환_기록과_원금잔액을_가져온다() {
            // given
            long accelerationId = insertAcceleration();

            // when
            Optional<AccelerationDailyChargeTargetResponse> result =
                    accelerationDailyChargeMapper.findAccrualTarget(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(target -> {
                assertThat(target.getLoanAccelerationId()).isEqualTo(accelerationId);
                assertThat(target.getAppliedBaseRate()).isEqualByComparingTo("4.5");
                assertThat(target.getAppliedAdditionalRate()).isEqualByComparingTo("1.5");
                assertThat(target.getCarriedInterest()).isEqualByComparingTo("78000");
                assertThat(target.getPaidCarriedInterest()).isEqualByComparingTo("8000");
                assertThat(target.getCarriedOverduePrincipal()).isEqualByComparingTo("1502000");
                assertThat(target.getPaidCarriedOverduePrincipal()).isEqualByComparingTo("2000");
                assertThat(target.getLoanPrincipalBalance()).isEqualByComparingTo("10000000");
            });
        }

        @Test
        @DisplayName("전환 기록이 없으면 빈 값이다")
        void 전환_기록이_없으면_빈_값이다() {
            // when
            Optional<AccelerationDailyChargeTargetResponse> result =
                    accelerationDailyChargeMapper.findAccrualTarget(loanAccountId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("그날 쌓았는지 확인할 때")
    class 그날_행_확인 {

        @Test
        @DisplayName("10/15 행이 있으면 10/15로 확인할 때 true다")
        void 그날_행이_있으면_true다() {
            // given
            long accelerationId = insertAcceleration();
            insertDailyCharge(accelerationId, "2026-10-15");

            // when
            boolean result = accelerationDailyChargeMapper.existsByLoanAccelerationIdAndChargeDate(accelerationId, CHARGE_DATE);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("10/14 행만 있으면 10/15로 확인할 때 false다")
        void 전날_행만_있으면_false다() {
            // given
            long accelerationId = insertAcceleration();
            insertDailyCharge(accelerationId, "2026-10-14");

            // when
            boolean result = accelerationDailyChargeMapper.existsByLoanAccelerationIdAndChargeDate(accelerationId, CHARGE_DATE);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("연체금을 기록할 때")
    class 연체금_기록 {

        @Test
        @DisplayName("(10/15, 23, 1,570,000원, 9%, 연체금 387원, 약정이자 1,397원, 진행중)을 넣으면 그 값 그대로 1행이 생기고 낸 연체금·낸 이자는 0원이다")
        void 넘긴_값_그대로_1행을_넣는다() {
            // given
            long accelerationId = insertAcceleration();
            AccelerationDailyChargeResponse charge = AccelerationDailyChargeResponse.builder()
                    .loanAccelerationId(accelerationId)
                    .chargeDate(CHARGE_DATE)
                    .loanJudgmentStatusId(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6)
                    .delinquencyTargetAmount(new BigDecimal("1570000"))
                    .delinquencyRate(new BigDecimal("9"))
                    .delinquencyCharge(new BigDecimal("387"))
                    .interestAmount(new BigDecimal("1397"))
                    .delinquencyStatusId(DelinquencyStatus.IN_PROGRESS)
                    .build();

            // when
            accelerationDailyChargeMapper.insertCharge(charge);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT charge_date, loan_judgment_status_id, delinquency_target_amount, delinquency_rate, "
                            + "delinquency_charge, paid_delinquency_charge, interest_amount, paid_interest, "
                            + "delinquency_status_id FROM acceleration_daily_charge WHERE loan_acceleration_id = ?",
                    accelerationId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("charge_date").toString()).isEqualTo("2026-10-15");
            assertThat(row.get("loan_judgment_status_id")).isEqualTo(23);
            assertThat((BigDecimal) row.get("delinquency_target_amount")).isEqualByComparingTo("1570000");
            assertThat((BigDecimal) row.get("delinquency_rate")).isEqualByComparingTo("9");
            assertThat((BigDecimal) row.get("delinquency_charge")).isEqualByComparingTo("387");
            assertThat((BigDecimal) row.get("interest_amount")).isEqualByComparingTo("1397");
            assertThat((BigDecimal) row.get("paid_delinquency_charge")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_interest")).isEqualByComparingTo("0");
            assertThat(row.get("delinquency_status_id")).isEqualTo(1);
        }
    }

    private long insertAcceleration() {
        return insert("INSERT INTO loan_acceleration (loan_account_id, converted_date, applied_base_rate, "
                + "applied_additional_rate, carried_interest, paid_carried_interest, carried_overdue_principal, "
                + "paid_carried_overdue_principal, carried_delinquency_charge) "
                + "VALUES (?, '2026-10-11', 4.5, 1.5, 78000, 8000, 1502000, 2000, 500)", loanAccountId);
    }

    private void insertDailyCharge(long loanAccelerationId, String chargeDate) {
        insert("INSERT INTO acceleration_daily_charge (loan_acceleration_id, charge_date, loan_judgment_status_id, "
                + "delinquency_target_amount, delinquency_rate, delinquency_charge, delinquency_status_id) "
                + "VALUES (?, ?, 22, 10000000, 9, 2465, 1)", loanAccelerationId, chargeDate);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
