package com.repayment.domain.delinquency.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeResponse;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeTargetResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("회차 연체금 매퍼")
class InstallmentDelinquencyChargeMapperIntegrationTest {

    // repayment_schedule_status
    private static final int DUE = 2;
    private static final int PAID = 3;
    private static final int OVERDUE = 4;

    private static final LocalDate CHARGE_DATE = LocalDate.of(2026, 10, 15);

    @Autowired
    private InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long productId;
    private long loanAccountId;

    // 원리금균등·고정금리, 약정 10,000,000원, 12회
    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('charge-test', 'pw', '테스트')");
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        loanAccountId = insertLoanAccount("900-000-000003");
    }

    @Nested
    @DisplayName("쌓을 회차를 조회할 때")
    class 쌓을_회차_조회 {

        @Test
        @DisplayName("연체 1·2회, 납부 기한 도래 3회, 납입 완료 4회와 다른 대출의 연체 회차가 있으면 이 대출의 연체 1·2회만 회차 순서로 가져온다")
        void 그_대출의_연체_회차만_가져온다() {
            // given
            long second = insertSchedule(loanAccountId, 2, OVERDUE);
            long first = insertSchedule(loanAccountId, 1, OVERDUE);
            insertSchedule(loanAccountId, 3, DUE);
            insertSchedule(loanAccountId, 4, PAID);
            insertSchedule(insertLoanAccount("900-000-000004"), 1, OVERDUE);

            // when
            List<InstallmentDelinquencyChargeTargetResponse> result =
                    installmentDelinquencyChargeMapper.findAccrualTargets(loanAccountId, CHARGE_DATE);

            // then
            assertThat(result).extracting(InstallmentDelinquencyChargeTargetResponse::getRepaymentScheduleId)
                    .containsExactly(first, second);
            InstallmentDelinquencyChargeTargetResponse target = result.get(0);
            assertThat(target.getScheduledPrincipal()).isEqualByComparingTo("800000");
            assertThat(target.getScheduledInterest()).isEqualByComparingTo("50000");
            assertThat(target.getPaidPrincipal()).isEqualByComparingTo("0");
            assertThat(target.getPaidInterest()).isEqualByComparingTo("20000");
            assertThat(target.getAppliedBaseRate()).isEqualByComparingTo("4.5");
            assertThat(target.getAppliedAdditionalRate()).isEqualByComparingTo("1.5");
        }

        @Test
        @DisplayName("연체 1회는 10/15 행이 있고 연체 2회는 10/14 행만 있으면 10/15로 조회할 때 연체 2회만 가져온다")
        void 그날_이미_쌓은_회차는_빠진다() {
            // given
            long first = insertSchedule(loanAccountId, 1, OVERDUE);
            long second = insertSchedule(loanAccountId, 2, OVERDUE);
            insertChargeRow(first, "2026-10-15");
            insertChargeRow(second, "2026-10-14");

            // when
            List<InstallmentDelinquencyChargeTargetResponse> result =
                    installmentDelinquencyChargeMapper.findAccrualTargets(loanAccountId, CHARGE_DATE);

            // then
            assertThat(result).extracting(InstallmentDelinquencyChargeTargetResponse::getRepaymentScheduleId)
                    .containsExactly(second);
        }
    }

    @Nested
    @DisplayName("연체금을 기록할 때")
    class 연체금_기록 {

        @Test
        @DisplayName("(회차, 2026-10-15, 21, 830,000원, 9%, 204원, 진행중)을 넣으면 그 값 그대로 1행이 생기고 낸 연체금은 0원이다")
        void 넘긴_값_그대로_1행을_넣는다() {
            // given
            long scheduleId = insertSchedule(loanAccountId, 1, OVERDUE);
            InstallmentDelinquencyChargeResponse charge = InstallmentDelinquencyChargeResponse.builder()
                    .repaymentScheduleId(scheduleId)
                    .chargeDate(CHARGE_DATE)
                    .loanJudgmentStatusId(LoanJudgmentStatus.INSTALLMENT_OVERDUE)
                    .delinquencyTargetAmount(new BigDecimal("830000"))
                    .delinquencyRate(new BigDecimal("9"))
                    .delinquencyCharge(new BigDecimal("204"))
                    .delinquencyStatusId(DelinquencyStatus.IN_PROGRESS)
                    .build();

            // when
            installmentDelinquencyChargeMapper.insertCharge(charge);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT charge_date, loan_judgment_status_id, delinquency_target_amount, delinquency_rate, "
                            + "delinquency_charge, paid_delinquency_charge, delinquency_status_id "
                            + "FROM installment_delinquency_charge WHERE repayment_schedule_id = ?", scheduleId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("charge_date").toString()).isEqualTo("2026-10-15");
            assertThat(row.get("loan_judgment_status_id")).isEqualTo(21);
            assertThat((BigDecimal) row.get("delinquency_target_amount")).isEqualByComparingTo("830000");
            assertThat((BigDecimal) row.get("delinquency_rate")).isEqualByComparingTo("9");
            assertThat((BigDecimal) row.get("delinquency_charge")).isEqualByComparingTo("204");
            assertThat((BigDecimal) row.get("paid_delinquency_charge")).isEqualByComparingTo("0");
            assertThat(row.get("delinquency_status_id")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("충당한 연체금을 더할 때")
    class 충당한_연체금_더하기 {

        @Test
        @DisplayName("연체금 300원 중 100원 낸 진행중 행에 200원을 더하면 낸 연체금은 300원이고 상태는 진행중 그대로다")
        void 금액만_더하고_상태는_바꾸지_않는다() {
            // given
            long chargeId = insertChargeRow(insertSchedule(loanAccountId, 1, OVERDUE), "300", "100");

            // when
            installmentDelinquencyChargeMapper.updateAddPaidDelinquencyCharge(chargeId, new BigDecimal("200"));

            // then
            Map<String, Object> row = findChargeRow(chargeId);
            assertThat((BigDecimal) row.get("paid_delinquency_charge")).isEqualByComparingTo("300");
            assertThat(row.get("delinquency_status_id")).isEqualTo(1);
        }

        private long insertChargeRow(long repaymentScheduleId, String charge, String paid) {
            return insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, "
                            + "loan_judgment_status_id, delinquency_target_amount, delinquency_rate, delinquency_charge, "
                            + "paid_delinquency_charge, delinquency_status_id) VALUES (?, '2026-10-11', 21, 830000, 9, ?, ?, 1)",
                    repaymentScheduleId, new BigDecimal(charge), new BigDecimal(paid));
        }

        private Map<String, Object> findChargeRow(long chargeId) {
            return jdbcTemplate.queryForMap("SELECT paid_delinquency_charge, delinquency_status_id "
                    + "FROM installment_delinquency_charge WHERE installment_delinquency_charge_id = ?", chargeId);
        }
    }

    @Nested
    @DisplayName("다 낸 연체금을 해소로 바꿀 때")
    class 해소 {

        @Test
        @DisplayName("연체금 1,200원을 모두 낸 진행중 행은 해소가 된다")
        void 다_낸_행은_해소가_된다() {
            // given
            long chargeId = insertChargeRow(insertSchedule(loanAccountId, 1, OVERDUE), "1200", "1200");

            // when
            installmentDelinquencyChargeMapper.updateResolveFullyPaid(loanAccountId);

            // then
            assertThat(findStatus(chargeId)).isEqualTo(2);
        }

        @Test
        @DisplayName("연체금 1,200원 중 1,199원을 낸 진행중 행은 진행중 그대로다")
        void 덜_낸_행은_진행중_그대로다() {
            // given
            long chargeId = insertChargeRow(insertSchedule(loanAccountId, 1, OVERDUE), "1200", "1199");

            // when
            installmentDelinquencyChargeMapper.updateResolveFullyPaid(loanAccountId);

            // then
            assertThat(findStatus(chargeId)).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 대출의 연체금을 모두 낸 진행중 행은 진행중 그대로다")
        void 다른_대출의_행은_그대로다() {
            // given
            long otherLoanAccountId = insertLoanAccount("900-000-000004");
            long chargeId = insertChargeRow(insertSchedule(otherLoanAccountId, 1, OVERDUE), "1200", "1200");

            // when
            installmentDelinquencyChargeMapper.updateResolveFullyPaid(loanAccountId);

            // then
            assertThat(findStatus(chargeId)).isEqualTo(1);
        }

        private long insertChargeRow(long repaymentScheduleId, String charge, String paid) {
            return insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, "
                            + "loan_judgment_status_id, delinquency_target_amount, delinquency_rate, delinquency_charge, "
                            + "paid_delinquency_charge, delinquency_status_id) VALUES (?, '2026-10-11', 21, 830000, 9, ?, ?, 1)",
                    repaymentScheduleId, new BigDecimal(charge), new BigDecimal(paid));
        }

        private int findStatus(long chargeId) {
            return jdbcTemplate.queryForObject("SELECT delinquency_status_id FROM installment_delinquency_charge "
                    + "WHERE installment_delinquency_charge_id = ?", Integer.class, chargeId);
        }
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_bank_code, repayment_account_no, "
                + "account_no, loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, '999', '110-000-000001', ?, 2, 10000000, 10, 0)", contractId, accountNo);
    }

    // 원금 800,000 / 이자 50,000 / 낸 이자 20,000 / 적용 금리 4.5 + 1.5
    private long insertSchedule(long loanAccountId, int installmentNo, int statusId) {
        return insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at, paid_interest) "
                        + "VALUES (?, ?, ?, '2026-09-10', 800000, 50000, 4.5, 1.5, NOW(), 20000)",
                loanAccountId, installmentNo, statusId);
    }

    private void insertChargeRow(long repaymentScheduleId, String chargeDate) {
        insert("INSERT INTO installment_delinquency_charge (repayment_schedule_id, charge_date, loan_judgment_status_id, "
                + "delinquency_target_amount, delinquency_rate, delinquency_charge, delinquency_status_id) "
                + "VALUES (?, ?, 21, 830000, 9, 204, 1)", repaymentScheduleId, chargeDate);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
