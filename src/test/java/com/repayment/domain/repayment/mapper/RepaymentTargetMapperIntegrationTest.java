package com.repayment.domain.repayment.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.dto.response.UnpaidAccelerationResponse;
import com.repayment.domain.repayment.dto.response.UnpaidDailyChargeResponse;
import com.repayment.domain.repayment.dto.response.UnpaidInstallmentResponse;
import com.repayment.domain.repayment.dto.response.UnpaidMaturityOverdueResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("갚을 대상 매퍼")
class RepaymentTargetMapperIntegrationTest {

    // repayment_schedule_status
    private static final int SCHEDULED = 1;
    private static final int DUE = 2;
    private static final int PAID = 3;
    private static final int OVERDUE = 4;
    private static final int CLOSED_BY_ACCELERATION = 5;

    @Autowired
    private RepaymentTargetMapper repaymentTargetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long accountId;
    private long productId;
    private long loanAccountId;

    // 원금잔액 2,002,000원
    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('repayable-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000009', 0, 1)", memberId);
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (2, 2, '테스트 상품', 1.5)");
        loanAccountId = insertLoanAccount("900-000-000101");
    }

    @Nested
    @DisplayName("회차 연체금을 조회할 때")
    class 회차_연체금 {

        @Test
        @DisplayName("연체 회차의 10/12(300원)·10/11(300원 중 100원 냄)·10/10(다 냄)과 닫힌 회차·다른 대출의 연체금이 있으면 10/11(200원) → 10/12(300원)만 나온다")
        void 그_대출_열린_회차의_남은_연체금만_날짜순으로_나온다() {
            // given
            long overdue = insertSchedule(loanAccountId, 2, OVERDUE, "2026-10-09", "500000", "50000", "0", "0");
            long closed = insertSchedule(loanAccountId, 1, CLOSED_BY_ACCELERATION, "2026-09-09", "500000", "50000", "0", "0");
            long oct12 = insertInstallmentCharge(overdue, "2026-10-12", "300", "0");
            long oct11 = insertInstallmentCharge(overdue, "2026-10-11", "300", "100");
            insertInstallmentCharge(overdue, "2026-10-10", "300", "300");
            insertInstallmentCharge(closed, "2026-10-11", "300", "0");
            long otherLoan = insertLoanAccount("900-000-000102");
            long otherOverdue = insertSchedule(otherLoan, 2, OVERDUE, "2026-10-09", "500000", "50000", "0", "0");
            insertInstallmentCharge(otherOverdue, "2026-10-11", "300", "0");

            // when
            List<UnpaidDailyChargeResponse> result = repaymentTargetMapper.findUnpaidInstallmentDelinquencyCharges(loanAccountId);

            // then
            assertThat(result).extracting(UnpaidDailyChargeResponse::getChargeId).containsExactly(oct11, oct12);
            assertThat(result.get(0).getUnpaidDelinquencyCharge()).isEqualByComparingTo("200");
            assertThat(result.get(1).getUnpaidDelinquencyCharge()).isEqualByComparingTo("300");
            assertThat(result).allSatisfy(charge -> assertThat(charge.getUnpaidInterest()).isEqualByComparingTo("0"));
        }
    }

    @Nested
    @DisplayName("회차를 조회할 때")
    class 회차 {

        @Test
        @DisplayName("1회 납입 완료, 2회 연체(이자 50,000원 중 20,000원 냄), 3회 납부 기한 도래, 4회 예정, 다른 대출 회차가 있으면 2회(이자 30,000원) → 3회만 나온다")
        void 연체와_납부_기한_도래_회차만_예정일순으로_나온다() {
            // given
            long third = insertSchedule(loanAccountId, 3, DUE, "2026-12-09", "500000", "700", "0", "0");
            insertSchedule(loanAccountId, 4, SCHEDULED, "2027-01-09", "0", "0", "0", "0");
            insertSchedule(loanAccountId, 1, PAID, "2026-10-09", "500000", "60000", "500000", "60000");
            long second = insertSchedule(loanAccountId, 2, OVERDUE, "2026-11-09", "500000", "50000", "0", "20000");
            long otherLoan = insertLoanAccount("900-000-000102");
            insertSchedule(otherLoan, 2, OVERDUE, "2026-11-09", "500000", "50000", "0", "0");

            // when
            List<UnpaidInstallmentResponse> result = repaymentTargetMapper.findUnpaidInstallments(loanAccountId);

            // then
            assertThat(result).extracting(UnpaidInstallmentResponse::getRepaymentScheduleId).containsExactly(second, third);
            assertThat(result.get(0).getUnpaidInterest()).isEqualByComparingTo("30000");
            assertThat(result.get(0).getUnpaidPrincipal()).isEqualByComparingTo("500000");
            assertThat(result.get(1).getUnpaidInterest()).isEqualByComparingTo("700");
            assertThat(result.get(1).getUnpaidPrincipal()).isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("기한이익상실을 조회할 때")
    class 기한이익상실 {

        @Test
        @DisplayName("옮긴 연체금 500원 중 100원, 이자 78,000원 중 8,000원, 밀린 원금 1,502,000원 중 2,000원을 냈으면 남은 금액은 400원·70,000원·1,500,000원이고 원금잔액은 2,002,000원이다")
        void 옮긴_금액의_남은_부분과_원금잔액이_나온다() {
            // given
            long accelerationId = insertAcceleration(loanAccountId);

            // when
            Optional<UnpaidAccelerationResponse> result = repaymentTargetMapper.findUnpaidAcceleration(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(acceleration -> {
                assertThat(acceleration.getLoanAccelerationId()).isEqualTo(accelerationId);
                assertThat(acceleration.getUnpaidCarriedDelinquencyCharge()).isEqualByComparingTo("400");
                assertThat(acceleration.getUnpaidCarriedInterest()).isEqualByComparingTo("70000");
                assertThat(acceleration.getUnpaidCarriedOverduePrincipal()).isEqualByComparingTo("1500000");
                assertThat(acceleration.getLoanPrincipalBalance()).isEqualByComparingTo("2002000");
            });
        }

        @Test
        @DisplayName("기한이익상실 전환 기록이 없는 대출이면 빈 값이다")
        void 전환_기록이_없으면_빈_값이다() {
            // when
            Optional<UnpaidAccelerationResponse> result = repaymentTargetMapper.findUnpaidAcceleration(loanAccountId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("하루 기록 10/12(연체금 남음)·10/11(연체금은 다 내고 약정이자 400원 남음)·10/10(다 냄)이 있으면 10/11 → 10/12가 나온다")
        void 약정이자만_남은_하루_기록도_나온다() {
            // given
            long accelerationId = insertAcceleration(loanAccountId);
            long oct12 = insertAccelerationDailyCharge(accelerationId, "2026-10-12", "100", "0", "0", "0");
            long oct11 = insertAccelerationDailyCharge(accelerationId, "2026-10-11", "100", "100", "400", "0");
            insertAccelerationDailyCharge(accelerationId, "2026-10-10", "100", "100", "400", "400");

            // when
            List<UnpaidDailyChargeResponse> result = repaymentTargetMapper.findUnpaidAccelerationDailyCharges(accelerationId);

            // then
            assertThat(result).extracting(UnpaidDailyChargeResponse::getChargeId).containsExactly(oct11, oct12);
            assertThat(result.get(0).getUnpaidDelinquencyCharge()).isEqualByComparingTo("0");
            assertThat(result.get(0).getUnpaidInterest()).isEqualByComparingTo("400");
            assertThat(result.get(1).getUnpaidDelinquencyCharge()).isEqualByComparingTo("100");
            assertThat(result.get(1).getUnpaidInterest()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("만기 경과를 조회할 때")
    class 만기_경과 {

        @Test
        @DisplayName("옮긴 연체금 2,000원 중 500원, 이자 50,000원 중 10,000원을 냈으면 남은 금액은 1,500원·40,000원이고 원금잔액은 2,002,000원이다. 전환 기록이 없는 대출은 빈 값이다")
        void 옮긴_금액의_남은_부분과_원금잔액이_나온다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            long otherLoan = insertLoanAccount("900-000-000102");

            // when
            Optional<UnpaidMaturityOverdueResponse> result = repaymentTargetMapper.findUnpaidMaturityOverdue(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(maturityOverdue -> {
                assertThat(maturityOverdue.getLoanMaturityOverdueId()).isEqualTo(maturityOverdueId);
                assertThat(maturityOverdue.getUnpaidCarriedDelinquencyCharge()).isEqualByComparingTo("1500");
                assertThat(maturityOverdue.getUnpaidCarriedInterest()).isEqualByComparingTo("40000");
                assertThat(maturityOverdue.getLoanPrincipalBalance()).isEqualByComparingTo("2002000");
            });
            assertThat(repaymentTargetMapper.findUnpaidMaturityOverdue(otherLoan)).isEmpty();
        }

        @Test
        @DisplayName("하루 기록 10/12(500원)·10/11(500원)·10/10(다 냄)이 있으면 10/11 → 10/12가 나온다")
        void 남은_하루_기록만_날짜순으로_나온다() {
            // given
            long maturityOverdueId = insertMaturityOverdue(loanAccountId);
            long oct12 = insertMaturityOverdueDailyCharge(maturityOverdueId, "2026-10-12", "500", "0");
            long oct11 = insertMaturityOverdueDailyCharge(maturityOverdueId, "2026-10-11", "500", "0");
            insertMaturityOverdueDailyCharge(maturityOverdueId, "2026-10-10", "500", "500");

            // when
            List<UnpaidDailyChargeResponse> result =
                    repaymentTargetMapper.findUnpaidMaturityOverdueDailyCharges(maturityOverdueId);

            // then
            assertThat(result).extracting(UnpaidDailyChargeResponse::getChargeId).containsExactly(oct11, oct12);
            assertThat(result).allSatisfy(charge ->
                    assertThat(charge.getUnpaidDelinquencyCharge()).isEqualByComparingTo("500"));
        }
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-09-09', '2027-01-09', 3000000, 1.5, 4.5, 4)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, ?, 1, 2002000, 9, 3)", contractId, accountId, accountNo);
    }

    private long insertSchedule(long loanAccountId, int installmentNo, int statusId, String scheduledDate,
                                String principal, String interest, String paidPrincipal, String paidInterest) {
        return insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at, paid_principal, paid_interest) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 4.5, 1.5, NOW(), ?, ?)",
                loanAccountId, installmentNo, statusId, scheduledDate, new BigDecimal(principal),
                new BigDecimal(interest), new BigDecimal(paidPrincipal), new BigDecimal(paidInterest));
    }

    private long insertInstallmentCharge(long repaymentScheduleId, String chargeDate, String charge, String paid) {
        return insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, "
                        + "loan_judgment_status_id, delinquency_target_amount, delinquency_rate, delinquency_charge, "
                        + "paid_delinquency_charge, delinquency_status_id) VALUES (?, ?, 21, 550000, 9, ?, ?, 1)",
                repaymentScheduleId, chargeDate, new BigDecimal(charge), new BigDecimal(paid));
    }

    private long insertAcceleration(long loanAccountId) {
        return insert("INSERT INTO loan_acceleration (loan_account_id, converted_date, applied_base_rate, "
                + "applied_additional_rate, carried_interest, paid_carried_interest, carried_overdue_principal, "
                + "paid_carried_overdue_principal, carried_delinquency_charge, paid_carried_delinquency_charge) "
                + "VALUES (?, '2026-10-10', 4.5, 1.5, 78000, 8000, 1502000, 2000, 500, 100)", loanAccountId);
    }

    private long insertAccelerationDailyCharge(long loanAccelerationId, String chargeDate, String charge,
                                               String paidCharge, String interest, String paidInterest) {
        return insert("INSERT INTO acceleration_daily_charge (loan_acceleration_id, charge_date, loan_judgment_status_id, "
                        + "delinquency_target_amount, delinquency_rate, delinquency_charge, paid_delinquency_charge, "
                        + "interest_amount, paid_interest, delinquency_status_id) VALUES (?, ?, 23, 80000, 9, ?, ?, ?, ?, 1)",
                loanAccelerationId, chargeDate, new BigDecimal(charge), new BigDecimal(paidCharge),
                new BigDecimal(interest), new BigDecimal(paidInterest));
    }

    private long insertMaturityOverdue(long loanAccountId) {
        return insert("INSERT INTO loan_maturity_overdue (loan_account_id, converted_date, applied_base_rate, "
                + "applied_additional_rate, carried_interest, paid_carried_interest, carried_delinquency_charge, "
                + "paid_carried_delinquency_charge) VALUES (?, '2027-01-10', 4.5, 1.5, 50000, 10000, 2000, 500)",
                loanAccountId);
    }

    private long insertMaturityOverdueDailyCharge(long loanMaturityOverdueId, String chargeDate, String charge,
                                                  String paid) {
        return insert("INSERT INTO maturity_overdue_daily_charge (loan_maturity_overdue_id, charge_date, "
                        + "loan_judgment_status_id, delinquency_target_amount, delinquency_rate, delinquency_charge, "
                        + "paid_delinquency_charge, delinquency_status_id) VALUES (?, ?, 1, 2002000, 9, ?, ?, 1)",
                loanMaturityOverdueId, chargeDate, new BigDecimal(charge), new BigDecimal(paid));
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
