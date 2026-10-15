package com.repayment.domain.schedule.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.global.code.InterestType;
import com.repayment.global.code.RepaymentMethod;
import com.repayment.global.code.RepaymentScheduleStatus;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("대출 스케줄 매퍼")
class RepaymentScheduleMapperIntegrationTest {

    @Autowired
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanAccountId;
    private long firstScheduleId;
    private long thirdScheduleId;

    // 원리금균등·고정금리(기준 4.5% + 가산 1.5%), 원금 12,000,000원, 12회. 1·2회차 확정, 3회차 미확정
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('schedule-test', 'pw', '테스트')");
        long accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000001', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-09-10', '2027-09-10', 12000000, 1.5, 4.5, 12)", productId, memberId);
        loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, "
                + "loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000001', 1, 12000000, 10, 2)", contractId, accountId);
        firstScheduleId = insertConfirmedSchedule(1, "2026-10-10", "972797", "60000");
        insertConfirmedSchedule(2, "2026-11-10", "977661", "55136");
        thirdScheduleId = insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date) VALUES (?, 3, 1, '2026-12-10')", loanAccountId);
    }

    @Nested
    @DisplayName("확정 정보를 조회할 때")
    class 확정_정보_조회 {

        @Test
        @DisplayName("3회차를 조회하면 앞 회차 원금 합 1,950,458원, 상태 예정, 예정일, 상품·계약 값, 마지막 확정 회차 2가 나온다")
        void 앞_회차_원금만_더한다() {
            // given
            long repaymentScheduleId = thirdScheduleId;

            // when
            Optional<RepaymentScheduleConfirmTargetResponse> result =
                    repaymentScheduleMapper.findConfirmTarget(repaymentScheduleId);

            // then
            assertThat(result).isPresent();
            RepaymentScheduleConfirmTargetResponse target = result.get();
            assertThat(target.getRepaymentScheduleId()).isEqualTo(thirdScheduleId);
            assertThat(target.getLoanAccountId()).isEqualTo(loanAccountId);
            assertThat(target.getInstallmentNo()).isEqualTo(3);
            assertThat(target.getRepaymentScheduleStatusId()).isEqualTo(RepaymentScheduleStatus.SCHEDULED);
            assertThat(target.getScheduledDate()).isEqualTo(LocalDate.of(2026, 12, 10));
            assertThat(target.getInterestTypeId()).isEqualTo(InterestType.FIXED);
            assertThat(target.getRepaymentMethodId()).isEqualTo(RepaymentMethod.EQUAL_PRINCIPAL_INTEREST);
            assertThat(target.getContractBaseRate()).isEqualByComparingTo("4.5");
            assertThat(target.getContractAdditionalRate()).isEqualByComparingTo("1.5");
            assertThat(target.getContractPrincipal()).isEqualByComparingTo("12000000");
            assertThat(target.getTotalInstallments()).isEqualTo(12);
            assertThat(target.getLastConfirmedInstallmentNo()).isEqualTo(2);
            assertThat(target.getPriorScheduledPrincipalSum()).isEqualByComparingTo("1950458");
        }

        @Test
        @DisplayName("1회차를 조회하면 앞 회차가 없어 앞 회차 원금 합이 0원이다")
        void 앞_회차가_없으면_0원이다() {
            // given
            long repaymentScheduleId = firstScheduleId;

            // when
            Optional<RepaymentScheduleConfirmTargetResponse> result =
                    repaymentScheduleMapper.findConfirmTarget(repaymentScheduleId);

            // then
            assertThat(result).hasValueSatisfying(target ->
                    assertThat(target.getPriorScheduledPrincipalSum()).isEqualByComparingTo("0"));
        }

        @Test
        @DisplayName("없는 회차 ID를 조회하면 빈 값이 나온다")
        void 없는_회차는_빈_값이다() {
            // given
            long missingScheduleId = thirdScheduleId + 1000;

            // when
            Optional<RepaymentScheduleConfirmTargetResponse> result =
                    repaymentScheduleMapper.findConfirmTarget(missingScheduleId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("확정 내용을 저장할 때")
    class 확정_내용_저장 {

        @Test
        @DisplayName("3회차에 원금 982,549원, 이자 50,247원, 적용 금리 4.5%·1.5%를 넣고 confirmed_at을 채우며, 상태는 예정 그대로 둔다")
        void 확정_컬럼을_모두_저장한다() {
            // given
            RepaymentAmountResponse amount = new RepaymentAmountResponse(new BigDecimal("982549"), new BigDecimal("50247"));
            AppliedRateResponse rate = new AppliedRateResponse(new BigDecimal("4.5"), new BigDecimal("1.5"));

            // when
            int updatedCount = repaymentScheduleMapper.updateConfirmation(thirdScheduleId, amount, rate);

            // then
            assertThat(updatedCount).isEqualTo(1);
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT scheduled_principal, scheduled_interest, "
                    + "applied_base_rate, applied_additional_rate, confirmed_at, repayment_schedule_status_id "
                    + "FROM repayment_schedule WHERE repayment_schedule_id = ?", thirdScheduleId);
            assertThat((BigDecimal) row.get("scheduled_principal")).isEqualByComparingTo("982549");
            assertThat((BigDecimal) row.get("scheduled_interest")).isEqualByComparingTo("50247");
            assertThat((BigDecimal) row.get("applied_base_rate")).isEqualByComparingTo("4.5");
            assertThat((BigDecimal) row.get("applied_additional_rate")).isEqualByComparingTo("1.5");
            assertThat(row.get("confirmed_at")).isNotNull();
            assertThat(row.get("repayment_schedule_status_id")).isEqualTo(RepaymentScheduleStatus.SCHEDULED.getId());
        }

        @Test
        @DisplayName("마지막 확정 회차 2를 3으로 올린다")
        void 마지막_확정_회차를_올린다() {
            // given
            int installmentNo = 3;

            // when
            int updatedCount = repaymentScheduleMapper.updateLastConfirmedInstallmentNo(loanAccountId, installmentNo);

            // then
            assertThat(updatedCount).isEqualTo(1);
            Integer lastConfirmed = jdbcTemplate.queryForObject(
                    "SELECT last_confirmed_installment_no FROM loan_account WHERE loan_account_id = ?",
                    Integer.class, loanAccountId);
            assertThat(lastConfirmed).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("회차 상태를 바꿀 때")
    class 상태_변경 {

        @Test
        @DisplayName("납부 기한 도래인 1회차를 연체로 바꾼다")
        void 회차_상태를_바꾼다() {
            // given
            long repaymentScheduleId = firstScheduleId;

            // when
            int updatedCount = repaymentScheduleMapper.updateStatus(repaymentScheduleId, RepaymentScheduleStatus.OVERDUE);

            // then
            assertThat(updatedCount).isEqualTo(1);
            Integer statusId = jdbcTemplate.queryForObject(
                    "SELECT repayment_schedule_status_id FROM repayment_schedule WHERE repayment_schedule_id = ?",
                    Integer.class, repaymentScheduleId);
            assertThat(statusId).isEqualTo(RepaymentScheduleStatus.OVERDUE.getId());
        }
    }

    @Nested
    @DisplayName("전환하며 회차를 닫을 때")
    class 회차_닫기 {

        @Test
        @DisplayName("납부 기한 도래 1회·연체 2회·예정 3회는 기한이익상실로 종료되고, 납입 완료 4회와 다른 대출의 연체 회차는 그대로다")
        void 납입_완료가_아닌_회차를_모두_닫는다() {
            // given
            jdbcTemplate.update("UPDATE repayment_schedule SET repayment_schedule_status_id = 4 "
                    + "WHERE loan_account_id = ? AND installment_no = 2", loanAccountId);
            long paidScheduleId = insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                    + "repayment_schedule_status_id, scheduled_date) VALUES (?, 4, 3, '2027-01-10')", loanAccountId);
            long otherLoanAccountId = insertOtherLoanAccount();
            long otherScheduleId = insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                    + "repayment_schedule_status_id, scheduled_date) VALUES (?, 1, 4, '2026-10-10')", otherLoanAccountId);

            // when
            repaymentScheduleMapper.updateUnpaidInstallmentStatus(loanAccountId, RepaymentScheduleStatus.CLOSED_BY_ACCELERATION);

            // then
            assertThat(jdbcTemplate.queryForList(
                    "SELECT installment_no FROM repayment_schedule WHERE loan_account_id = ? "
                            + "AND repayment_schedule_status_id = ? ORDER BY installment_no",
                    Integer.class, loanAccountId, RepaymentScheduleStatus.CLOSED_BY_ACCELERATION.getId()))
                    .containsExactly(1, 2, 3);
            assertThat(findStatusId(paidScheduleId)).isEqualTo(RepaymentScheduleStatus.PAID.getId());
            assertThat(findStatusId(otherScheduleId)).isEqualTo(RepaymentScheduleStatus.OVERDUE.getId());
        }

        // loan_account는 계약당 1개라 같은 조건의 계약을 하나 더 만든다
        private long insertOtherLoanAccount() {
            long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                    + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                    + "SELECT lc.loan_product_id, lc.member_id, lc.contract_date, lc.maturity_date, lc.contract_principal, "
                    + "lc.contract_additional_rate, lc.contract_base_rate, lc.total_installments "
                    + "FROM loan_contract lc JOIN loan_account la ON la.loan_contract_id = lc.loan_contract_id "
                    + "WHERE la.loan_account_id = ?", loanAccountId);
            return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                    + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                    + "SELECT ?, repayment_account_id, '900-000-000009', 1, 12000000, 10, 0 "
                    + "FROM loan_account WHERE loan_account_id = ?", contractId, loanAccountId);
        }

        private int findStatusId(long repaymentScheduleId) {
            return jdbcTemplate.queryForObject(
                    "SELECT repayment_schedule_status_id FROM repayment_schedule WHERE repayment_schedule_id = ?",
                    Integer.class, repaymentScheduleId);
        }
    }

    private long insertConfirmedSchedule(int installmentNo, String scheduledDate, String principal, String interest) {
        return insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at) VALUES (?, ?, 2, ?, ?, ?, 4.5, 1.5, NOW())",
                loanAccountId, installmentNo, scheduledDate, new BigDecimal(principal), new BigDecimal(interest));
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
