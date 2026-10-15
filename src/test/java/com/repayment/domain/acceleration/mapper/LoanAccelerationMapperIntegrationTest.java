package com.repayment.domain.acceleration.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.acceleration.dto.response.LoanAccelerationCarryOverResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("기한이익상실 매퍼")
class LoanAccelerationMapperIntegrationTest {

    // repayment_schedule_status
    private static final int DUE = 2;
    private static final int PAID = 3;
    private static final int OVERDUE = 4;

    @Autowired
    private LoanAccelerationMapper loanAccelerationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long accountId;
    private long productId;
    private long loanAccountId;

    // 원리금균등, 약정 12,000,000원, 12회
    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('acceleration-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000004', 0, 1)", memberId);
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (2, 2, '테스트 상품', 1.5)");
        loanAccountId = insertLoanAccount("900-000-000005");
    }

    @Nested
    @DisplayName("전환했는지 조회할 때")
    class 전환_여부_조회 {

        @Test
        @DisplayName("다른 대출의 기한이익상실 행만 있으면 false다")
        void 다른_대출_행만_있으면_false다() {
            // given
            insertAccelerationRow(insertLoanAccount("900-000-000006"));

            // when
            boolean result = loanAccelerationMapper.existsByLoanAccountId(loanAccountId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("그 대출의 기한이익상실 행이 있으면 true다")
        void 그_대출_행이_있으면_true다() {
            // given
            insertAccelerationRow(loanAccountId);

            // when
            boolean result = loanAccelerationMapper.existsByLoanAccountId(loanAccountId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("옮길 금액을 조회할 때")
    class 옮길_금액_조회 {

        @Test
        @DisplayName("연체 1회(9/10, 6%)·2회(10/10, 6.5%)만 더해 이자 78,000원·원금 1,502,000원이고, 금리는 가장 오래된 1회의 6%다")
        void 연체_회차의_남은_금액을_더하고_가장_오래된_회차의_금리를_쓴다() {
            // given
            insertSchedule(2, OVERDUE, "2026-10-10", "802000", "48000", "0", "0", "5.0");
            insertSchedule(1, OVERDUE, "2026-09-10", "800000", "50000", "100000", "20000", "4.5");
            insertSchedule(3, DUE, "2026-11-10", "804000", "46000", "0", "0", "5.0");
            insertSchedule(4, PAID, "2026-08-10", "798000", "52000", "798000", "52000", "4.5");

            // when
            LoanAccelerationCarryOverResponse result = loanAccelerationMapper.findCarryOver(loanAccountId);

            // then
            assertThat(result.getAppliedInterestRate()).isEqualByComparingTo("6");
            assertThat(result.getCarriedInterest()).isEqualByComparingTo("78000");
            assertThat(result.getCarriedOverduePrincipal()).isEqualByComparingTo("1502000");
            assertThat(result.getCarriedDelinquencyCharge()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("연체 1회 연체금 300원(다 냄)·300원, 연체 2회 연체금 200원이 있고 다른 대출 연체금 1,000원이 있으면 옮길 연체금은 500원이다")
        void 그_대출_연체_회차의_미납_연체금만_더한다() {
            // given
            long first = insertSchedule(1, OVERDUE, "2026-09-10", "800000", "50000", "0", "0", "4.5");
            long second = insertSchedule(2, OVERDUE, "2026-10-10", "802000", "48000", "0", "0", "4.5");
            insertCharge(first, "2026-09-11", "300", "300");
            insertCharge(first, "2026-09-12", "300", "0");
            insertCharge(second, "2026-10-11", "200", "0");
            long otherLoanAccountId = insertLoanAccount("900-000-000006");
            long other = insertScheduleOf(otherLoanAccountId, 1, OVERDUE, "2026-09-10", "800000", "50000", "0", "0", "4.5");
            insertCharge(other, "2026-09-11", "1000", "0");

            // when
            LoanAccelerationCarryOverResponse result = loanAccelerationMapper.findCarryOver(loanAccountId);

            // then
            assertThat(result.getCarriedDelinquencyCharge()).isEqualByComparingTo("500");
        }
    }

    @Nested
    @DisplayName("기한이익상실을 기록할 때")
    class 기한이익상실_기록 {

        @Test
        @DisplayName("(2026-10-15, 6%, 이자 78,000원, 원금 1,502,000원, 연체금 500원)을 넣으면 그 값 그대로 1행이 생기고 낸 금액은 모두 0원이다")
        void 넘긴_값_그대로_1행을_넣는다() {
            // given
            LoanAccelerationCarryOverResponse carryOver = LoanAccelerationCarryOverResponse.builder()
                    .appliedInterestRate(new BigDecimal("6"))
                    .carriedInterest(new BigDecimal("78000"))
                    .carriedOverduePrincipal(new BigDecimal("1502000"))
                    .carriedDelinquencyCharge(new BigDecimal("500"))
                    .build();

            // when
            loanAccelerationMapper.insertAcceleration(loanAccountId, LocalDate.of(2026, 10, 15), carryOver);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT converted_date, applied_interest_rate, carried_interest, paid_carried_interest, "
                            + "carried_overdue_principal, paid_carried_overdue_principal, "
                            + "carried_delinquency_charge, paid_carried_delinquency_charge "
                            + "FROM loan_acceleration WHERE loan_account_id = ?", loanAccountId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("converted_date").toString()).isEqualTo("2026-10-15");
            assertThat((BigDecimal) row.get("applied_interest_rate")).isEqualByComparingTo("6");
            assertThat((BigDecimal) row.get("carried_interest")).isEqualByComparingTo("78000");
            assertThat((BigDecimal) row.get("carried_overdue_principal")).isEqualByComparingTo("1502000");
            assertThat((BigDecimal) row.get("carried_delinquency_charge")).isEqualByComparingTo("500");
            assertThat((BigDecimal) row.get("paid_carried_interest")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_carried_overdue_principal")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_carried_delinquency_charge")).isEqualByComparingTo("0");
        }
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-07-10', '2027-07-10', 12000000, 1.5, 4.5, 12)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, ?, 2, 12000000, 10, 0)", contractId, accountId, accountNo);
    }

    // 가산금리는 1.5%로 같고 기준금리만 다르게 넣는다
    private long insertSchedule(int installmentNo, int statusId, String scheduledDate, String principal,
                                String interest, String paidPrincipal, String paidInterest, String baseRate) {
        return insertScheduleOf(loanAccountId, installmentNo, statusId, scheduledDate, principal, interest,
                paidPrincipal, paidInterest, baseRate);
    }

    private long insertScheduleOf(long loanAccountId, int installmentNo, int statusId, String scheduledDate,
                                  String principal, String interest, String paidPrincipal, String paidInterest,
                                  String baseRate) {
        return insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at, paid_principal, paid_interest) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1.5, NOW(), ?, ?)",
                loanAccountId, installmentNo, statusId, scheduledDate, new BigDecimal(principal),
                new BigDecimal(interest), new BigDecimal(baseRate), new BigDecimal(paidPrincipal),
                new BigDecimal(paidInterest));
    }

    private void insertCharge(long repaymentScheduleId, String chargeDate, String charge, String paid) {
        insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, loan_judgment_status_id, "
                        + "delinquency_target_amount, delinquency_rate, delinquency_charge, paid_delinquency_charge, "
                        + "delinquency_status_id) VALUES (?, ?, 21, 850000, 9, ?, ?, 1)",
                repaymentScheduleId, chargeDate, new BigDecimal(charge), new BigDecimal(paid));
    }

    private void insertAccelerationRow(long loanAccountId) {
        insert("INSERT INTO loan_acceleration (loan_account_id, converted_date, applied_interest_rate, "
                + "carried_interest, carried_overdue_principal, carried_delinquency_charge) "
                + "VALUES (?, '2026-10-15', 6, 0, 0, 0)", loanAccountId);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
