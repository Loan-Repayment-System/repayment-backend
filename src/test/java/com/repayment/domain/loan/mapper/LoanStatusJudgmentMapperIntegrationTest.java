package com.repayment.domain.loan.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentMethod;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("대출 상태 판정 매퍼")
class LoanStatusJudgmentMapperIntegrationTest {

    // repayment_schedule_status
    private static final int DUE = 2;
    private static final int PAID = 3;
    private static final int OVERDUE = 4;
    private static final int CLOSED_BY_ACCELERATION = 5;

    @Autowired
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanAccountId;

    // 원리금균등·고정금리, 약정 30,000,000원, 만기 2027-06-30, 12회
    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('judgment-test', 'pw', '테스트')");
        long accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '110-000-000002', 0, 1)", memberId);
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-06-30', '2027-06-30', 30000000, 1.5, 4.5, 12)", productId, memberId);
        loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_account_id, account_no, "
                + "loan_status_id, loan_principal_balance, monthly_payment_day, last_confirmed_installment_no) "
                + "VALUES (?, ?, '900-000-000002', 1, 30000000, 10, 0)", contractId, accountId);
    }

    @Nested
    @DisplayName("판정할 값을 조회할 때")
    class 판정할_값_조회 {

        @Test
        @DisplayName("판정 행과 회차가 없으면 직전 0, 원리금균등, 약정 30,000,000원, 만기 2027-06-30, 밀린 회차 0개다")
        void 계약_상품_값을_가져오고_첫_판정은_정상으로_본다() {
            // when
            Optional<LoanStatusJudgmentTargetResponse> result =
                    loanStatusJudgmentMapper.findJudgmentTarget(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(target -> {
                assertThat(target.getLoanAccountId()).isEqualTo(loanAccountId);
                assertThat(target.getPreviousJudgmentStatus()).isEqualTo(LoanJudgmentStatus.NORMAL);
                assertThat(target.getRepaymentMethodId()).isEqualTo(RepaymentMethod.EQUAL_PRINCIPAL_INTEREST);
                assertThat(target.getContractPrincipal()).isEqualByComparingTo("30000000");
                assertThat(target.getMaturityDate()).isEqualTo(LocalDate.of(2027, 6, 30));
                assertThat(target.getOverdueInstallmentCount()).isZero();
                assertThat(target.getOldestOverdueDate()).isNull();
            });
        }

        @Test
        @DisplayName("판정 행이 10/1 11 → 10/2 0 → 10/2 21 순으로 쌓였으면 직전 판정은 마지막 행의 21이다")
        void 직전_판정은_id가_가장_큰_행이다() {
            // given
            insertJudgment("2026-10-01", 11);
            insertJudgment("2026-10-02", 0);
            insertJudgment("2026-10-02", 21);

            // when
            Optional<LoanStatusJudgmentTargetResponse> result =
                    loanStatusJudgmentMapper.findJudgmentTarget(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(target ->
                    assertThat(target.getPreviousJudgmentStatus()).isEqualTo(LoanJudgmentStatus.INSTALLMENT_OVERDUE));
        }

        @Test
        @DisplayName("연체 회차가 3/10·2/10 두 개고 다른 상태 회차가 섞여 있으면 밀린 회차 2개, 가장 이른 예정일 2/10이다")
        void 연체_회차만_세고_가장_이른_예정일을_고른다() {
            // given
            insertSchedule(1, OVERDUE, "2027-03-10");
            insertSchedule(2, OVERDUE, "2027-02-10");
            insertSchedule(3, DUE, "2027-04-10");
            insertSchedule(4, PAID, "2027-01-10");
            insertSchedule(5, CLOSED_BY_ACCELERATION, "2026-12-10");

            // when
            Optional<LoanStatusJudgmentTargetResponse> result =
                    loanStatusJudgmentMapper.findJudgmentTarget(loanAccountId);

            // then
            assertThat(result).hasValueSatisfying(target -> {
                assertThat(target.getOverdueInstallmentCount()).isEqualTo(2);
                assertThat(target.getOldestOverdueDate()).isEqualTo(LocalDate.of(2027, 2, 10));
            });
        }

        @Test
        @DisplayName("없는 대출 ID면 빈 값이다")
        void 없는_대출은_빈_값이다() {
            // when
            Optional<LoanStatusJudgmentTargetResponse> result =
                    loanStatusJudgmentMapper.findJudgmentTarget(loanAccountId + 1000);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("판정을 기록할 때")
    class 판정_기록 {

        @Test
        @DisplayName("(대출, 2026-10-14, 23)을 넣으면 그 대출의 판정 행 1개가 기준일 2026-10-14, 판정 23으로 남는다")
        void 대출_기준일_판정을_1행으로_남긴다() {
            // when
            loanStatusJudgmentMapper.insertJudgment(loanAccountId, LocalDate.of(2026, 10, 14),
                    LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);

            // then
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT business_date, loan_judgment_status_id FROM loan_status_judgment WHERE loan_account_id = ?",
                    loanAccountId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("business_date").toString()).isEqualTo("2026-10-14");
            assertThat(((Number) rows.get(0).get("loan_judgment_status_id")).intValue()).isEqualTo(23);
        }
    }

    private void insertJudgment(String businessDate, int statusId) {
        insert("INSERT INTO loan_status_judgment (loan_account_id, business_date, loan_judgment_status_id) "
                + "VALUES (?, ?, ?)", loanAccountId, businessDate, statusId);
    }

    private void insertSchedule(int installmentNo, int statusId, String scheduledDate) {
        insert("INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, "
                        + "scheduled_date, scheduled_principal, scheduled_interest, applied_base_rate, "
                        + "applied_additional_rate, confirmed_at) "
                        + "VALUES (?, ?, ?, ?, 2500000, 150000, 4.5, 1.5, NOW())",
                loanAccountId, installmentNo, statusId, scheduledDate);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
