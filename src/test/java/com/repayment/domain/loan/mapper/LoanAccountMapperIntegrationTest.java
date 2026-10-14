package com.repayment.domain.loan.mapper;

import com.repayment.TestcontainersConfig;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("대출 계좌 매퍼")
class LoanAccountMapperIntegrationTest {

    // repayment_schedule_status
    private static final int DUE = 2;
    private static final int PAID = 3;
    private static final int OVERDUE = 4;
    private static final int CLOSED_BY_ACCELERATION = 5;
    private static final int CLOSED_BY_MATURITY_OVERDUE = 6;

    @Autowired
    private LoanAccountMapper loanAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long contractId;
    private long accountId;

    // 만기일시상환·고정금리, 약정 10,000,000원, 12회
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('debt-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000001', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 3, '테스트 상품', 1.5)");
        contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
    }

    @Nested
    @DisplayName("남은 채무 합계를 조회할 때")
    class 남은_채무_합계 {

        @Test
        @DisplayName("회차 방식이면 원금잔액 10,000,000원에 확정 회차 이자 미납 80,000원과 연체금 미납 300원을 더한 10,080,300원이다")
        void 회차_방식은_확정_회차의_미납을_더한다() {
            // given
            long loanAccountId = insertLoanAccount("10000000");
            insertSchedule(loanAccountId, 1, DUE, "50000", "0");
            long overdue = insertSchedule(loanAccountId, 2, OVERDUE, "50000", "20000");
            insertUnconfirmedSchedule(loanAccountId, 3);
            insertInstallmentCharge(overdue, "2026-03-11", "300", "300");
            insertInstallmentCharge(overdue, "2026-03-12", "300", "0");

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("10080300"));
        }

        @Test
        @DisplayName("기한이익상실이면 닫힌 회차와 옮긴 밀린 원금은 빼고, 옮긴 이자 70,000원·옮긴 연체금 5,000원·하루 연체금 800원을 더한 10,075,800원이다")
        void 기한이익상실은_옮긴_금액과_하루_기록을_더한다() {
            // given
            long loanAccountId = insertLoanAccount("10000000");
            long closed = insertSchedule(loanAccountId, 1, CLOSED_BY_ACCELERATION, "50000", "0");
            insertInstallmentCharge(closed, "2026-02-11", "300", "0");
            long accelerationId = insert("INSERT INTO loan_acceleration (loan_account_id, converted_date, "
                    + "applied_interest_rate, carried_interest, paid_carried_interest, carried_overdue_principal, "
                    + "carried_delinquency_charge) VALUES (?, '2026-03-11', 6, 100000, 30000, 900000, 5000)",
                    loanAccountId);
            insert("INSERT INTO acceleration_daily_charge (loan_acceleration_id, charge_date, loan_judgment_status_id, "
                    + "delinquency_target_amount, delinquency_rate, delinquency_charge, interest_amount, paid_interest, "
                    + "delinquency_status_id) VALUES (?, '2026-03-11', 3, 100000, 9, 400, 1200, 1200, 1)", accelerationId);
            insert("INSERT INTO acceleration_daily_charge (loan_acceleration_id, charge_date, loan_judgment_status_id, "
                    + "delinquency_target_amount, delinquency_rate, delinquency_charge, delinquency_status_id) "
                    + "VALUES (?, '2026-03-12', 3, 100000, 9, 400, 1)", accelerationId);

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("10075800"));
        }

        @Test
        @DisplayName("만기 경과면 닫힌 회차는 빼고, 옮긴 이자 50,000원과 하루 연체금 1,000원을 더한 10,051,000원이다")
        void 만기_경과는_옮긴_금액과_하루_연체금을_더한다() {
            // given
            long loanAccountId = insertLoanAccount("10000000");
            long closed = insertSchedule(loanAccountId, 12, CLOSED_BY_MATURITY_OVERDUE, "50000", "0");
            insertInstallmentCharge(closed, "2027-01-11", "2000", "0");
            long maturityOverdueId = insert("INSERT INTO loan_maturity_overdue (loan_account_id, converted_date, "
                    + "applied_interest_rate, carried_interest, carried_overdue_principal, carried_delinquency_charge, "
                    + "paid_carried_delinquency_charge) VALUES (?, '2027-01-11', 6, 50000, 10000000, 2000, 2000)",
                    loanAccountId);
            insertMaturityOverdueCharge(maturityOverdueId, "2027-01-11");
            insertMaturityOverdueCharge(maturityOverdueId, "2027-01-12");

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("10051000"));
        }

        @Test
        @DisplayName("원금잔액 0원이고 회차 이자·연체금을 모두 냈으면 0원이다")
        void 다_갚았으면_0원이다() {
            // given
            long loanAccountId = insertLoanAccount("0");
            long paid = insertSchedule(loanAccountId, 1, PAID, "50000", "50000");
            insertInstallmentCharge(paid, "2026-02-11", "300", "300");

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("0"));
        }

        @Test
        @DisplayName("원금잔액 0원이어도 회차 연체금 3,000원이 남았으면 3,000원이다")
        void 원금이_0원이어도_연체금이_남으면_0원이_아니다() {
            // given
            long loanAccountId = insertLoanAccount("0");
            long paid = insertSchedule(loanAccountId, 1, PAID, "50000", "50000");
            insertInstallmentCharge(paid, "2026-02-11", "3000", "0");

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("3000"));
        }

        @Test
        @DisplayName("없는 대출 ID면 빈 값이다")
        void 없는_대출은_빈_값이다() {
            // given
            long missingLoanAccountId = insertLoanAccount("0") + 1000;

            // when
            Optional<BigDecimal> result = loanAccountMapper.findRemainingDebtTotal(missingLoanAccountId);

            // then
            assertThat(result).isEmpty();
        }
    }

    private long insertLoanAccount(String principalBalance) {
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000001', 1, ?, 10, 0)", contractId, accountId, new BigDecimal(principalBalance));
    }

    private long insertSchedule(long loanAccountId, int installmentNo, int statusId, String interest, String paidInterest) {
        return insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at, paid_interest) "
                        + "VALUES (?, ?, ?, '2026-02-10', 0, ?, 4.5, 1.5, NOW(), ?)",
                loanAccountId, installmentNo, statusId, new BigDecimal(interest), new BigDecimal(paidInterest));
    }

    private void insertUnconfirmedSchedule(long loanAccountId, int installmentNo) {
        insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                + "scheduled_date) VALUES (?, ?, 1, '2026-04-10')", loanAccountId, installmentNo);
    }

    private void insertInstallmentCharge(long repaymentScheduleId, String chargeDate, String charge, String paid) {
        insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, loan_judgment_status_id, "
                        + "delinquency_target_amount, delinquency_rate, delinquency_charge, paid_delinquency_charge, "
                        + "delinquency_status_id) VALUES (?, ?, 1, 50000, 9, ?, ?, 1)",
                repaymentScheduleId, chargeDate, new BigDecimal(charge), new BigDecimal(paid));
    }

    private void insertMaturityOverdueCharge(long loanMaturityOverdueId, String chargeDate) {
        insert("INSERT INTO maturity_overdue_daily_charge (loan_maturity_overdue_id, charge_date, "
                + "loan_judgment_status_id, delinquency_target_amount, delinquency_rate, delinquency_charge, "
                + "delinquency_status_id) VALUES (?, ?, 4, 10000000, 9, 500, 1)", loanMaturityOverdueId, chargeDate);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
