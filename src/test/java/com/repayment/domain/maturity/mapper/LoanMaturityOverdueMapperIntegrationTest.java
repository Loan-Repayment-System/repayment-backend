package com.repayment.domain.maturity.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueCarryOverResponse;
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
@DisplayName("만기 경과 매퍼")
class LoanMaturityOverdueMapperIntegrationTest {

    // repayment_schedule_status
    private static final int PAID = 3;
    private static final int OVERDUE = 4;

    @Autowired
    private LoanMaturityOverdueMapper loanMaturityOverdueMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long accountId;
    private long productId;
    private long loanAccountId;

    // 원리금균등, 약정 3,000,000원, 3회, 만기 2027-01-10
    @BeforeEach
    void setUp() {
        memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('maturity-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000005', 0, 1)", memberId);
        productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (2, 2, '테스트 상품', 1.5)");
        loanAccountId = insertLoanAccount("900-000-000007");
    }

    @Nested
    @DisplayName("전환했는지 조회할 때")
    class 전환_여부_조회 {

        @Test
        @DisplayName("다른 대출의 만기 경과 행만 있으면 false다")
        void 다른_대출_행만_있으면_false다() {
            // given
            insertMaturityOverdueRow(insertLoanAccount("900-000-000008"));

            // when
            boolean result = loanMaturityOverdueMapper.existsByLoanAccountId(loanAccountId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("그 대출의 만기 경과 행이 있으면 true다")
        void 그_대출_행이_있으면_true다() {
            // given
            insertMaturityOverdueRow(loanAccountId);

            // when
            boolean result = loanMaturityOverdueMapper.existsByLoanAccountId(loanAccountId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("옮길 금액을 조회할 때")
    class 옮길_금액_조회 {

        @Test
        @DisplayName("연체 2회(기준 4.5%, 이자 50,000원·낸 이자 20,000원)·3회(마지막, 기준 5.0%, 이자 48,000원)만 더해 이자 78,000원이고, 금리는 마지막 3회의 기준 5.0%·가산 1.5%다")
        void 연체_회차의_남은_이자를_더하고_마지막_회차의_금리를_쓴다() {
            // given
            insertSchedule(3, OVERDUE, "2027-01-10", "1002000", "48000", "0", "0", "5.0");
            insertSchedule(1, PAID, "2026-11-10", "998000", "52000", "998000", "52000", "4.0");
            insertSchedule(2, OVERDUE, "2026-12-10", "1000000", "50000", "0", "20000", "4.5");

            // when
            LoanMaturityOverdueCarryOverResponse result = loanMaturityOverdueMapper.findCarryOver(loanAccountId);

            // then
            assertThat(result.getAppliedBaseRate()).isEqualByComparingTo("5.0");
            assertThat(result.getAppliedAdditionalRate()).isEqualByComparingTo("1.5");
            assertThat(result.getCarriedInterest()).isEqualByComparingTo("78000");
            assertThat(result.getCarriedDelinquencyCharge()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("연체 2회 연체금 300원(다 냄)·300원, 연체 3회 연체금 200원, 납입 완료 1회 연체금 100원(다 냄), 다른 대출 연체금 1,000원이 있으면 옮길 연체금은 500원이다")
        void 그_대출_연체_회차의_미납_연체금만_더한다() {
            // given
            long first = insertSchedule(1, PAID, "2026-11-10", "998000", "52000", "998000", "52000", "4.0");
            long second = insertSchedule(2, OVERDUE, "2026-12-10", "1000000", "50000", "0", "0", "4.5");
            long third = insertSchedule(3, OVERDUE, "2027-01-10", "1002000", "48000", "0", "0", "5.0");
            insertCharge(first, "2026-11-11", "100", "100");
            insertCharge(second, "2026-12-11", "300", "300");
            insertCharge(second, "2026-12-12", "300", "0");
            insertCharge(third, "2027-01-11", "200", "0");
            long otherLoanAccountId = insertLoanAccount("900-000-000008");
            long other = insertScheduleOf(otherLoanAccountId, 3, OVERDUE, "2027-01-10", "1002000", "48000", "0", "0", "5.0");
            insertCharge(other, "2027-01-11", "1000", "0");

            // when
            LoanMaturityOverdueCarryOverResponse result = loanMaturityOverdueMapper.findCarryOver(loanAccountId);

            // then
            assertThat(result.getCarriedDelinquencyCharge()).isEqualByComparingTo("500");
        }
    }

    @Nested
    @DisplayName("만기 경과를 기록할 때")
    class 만기_경과_기록 {

        @Test
        @DisplayName("(2027-01-11, 기준 5.0%, 가산 1.5%, 이자 78,000원, 연체금 500원)을 넣으면 그 값 그대로 1행이 생기고 낸 금액은 모두 0원이다")
        void 넘긴_값_그대로_1행을_넣는다() {
            // given
            LoanMaturityOverdueCarryOverResponse carryOver = LoanMaturityOverdueCarryOverResponse.builder()
                    .appliedBaseRate(new BigDecimal("5.0"))
                    .appliedAdditionalRate(new BigDecimal("1.5"))
                    .carriedInterest(new BigDecimal("78000"))
                    .carriedDelinquencyCharge(new BigDecimal("500"))
                    .build();

            // when
            loanMaturityOverdueMapper.insertMaturityOverdue(loanAccountId, LocalDate.of(2027, 1, 11), carryOver);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT converted_date, applied_base_rate, applied_additional_rate, carried_interest, paid_carried_interest, "
                            + "carried_delinquency_charge, paid_carried_delinquency_charge "
                            + "FROM loan_maturity_overdue WHERE loan_account_id = ?", loanAccountId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("converted_date").toString()).isEqualTo("2027-01-11");
            assertThat((BigDecimal) row.get("applied_base_rate")).isEqualByComparingTo("5.0");
            assertThat((BigDecimal) row.get("applied_additional_rate")).isEqualByComparingTo("1.5");
            assertThat((BigDecimal) row.get("carried_interest")).isEqualByComparingTo("78000");
            assertThat((BigDecimal) row.get("carried_delinquency_charge")).isEqualByComparingTo("500");
            assertThat((BigDecimal) row.get("paid_carried_interest")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("paid_carried_delinquency_charge")).isEqualByComparingTo("0");
        }
    }

    // loan_account는 계약당 1개라 대출마다 계약을 만든다
    private long insertLoanAccount(String accountNo) {
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-10-10', '2027-01-10', 3000000, 1.5, 4.5, 3)", productId, memberId);
        return insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, loan_status_id, "
                + "loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, ?, 2, 2002000, 10, 3)", contractId, accountId, accountNo);
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
                        + "delinquency_status_id) VALUES (?, ?, 21, 1050000, 9, ?, ?, 1)",
                repaymentScheduleId, chargeDate, new BigDecimal(charge), new BigDecimal(paid));
    }

    private void insertMaturityOverdueRow(long loanAccountId) {
        insert("INSERT INTO loan_maturity_overdue (loan_account_id, converted_date, applied_base_rate, "
                + "applied_additional_rate, carried_interest, carried_delinquency_charge) "
                + "VALUES (?, '2027-01-11', 5.0, 1.5, 0, 0)", loanAccountId);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
