package com.repayment.domain.repayment.service;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.exception.CollectionFailedException;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * 집금 응답을 받지 못했을 때 지금 상환 처리(한 트랜잭션)에서 생기는 문제를 재현한다.
 * 테스트는 문제가 일어나는 것을 단언하고 통과한다.
 * 펌뱅킹 클라이언트는 목으로 바꾸고, 집금 요청이 클라이언트까지 왔으면 펌뱅킹은 이체를 끝낸 것으로 본다.
 * 상환 처리가 스스로 커밋·롤백하고 채번은 따로 커밋하므로 @Transactional로 롤백할 수 없다. 테스트마다 행을 지운다.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("집금 응답 유실 재현")
class RepaymentServiceIntegrationTest {

    private static final long LOAN_ACCOUNT_ID = 1L;
    private static final BigDecimal PRINCIPAL_BALANCE = new BigDecimal("12000000");
    // 1회차 원금 977,289 + 이자 50,000
    private static final BigDecimal FIRST_INSTALLMENT = new BigDecimal("1027289");
    private static final String SUCCESS = "0000";

    @Autowired
    private RepaymentService repaymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirmbankingClient firmbankingClient;

    @BeforeEach
    void setUp() {
        insertLoanWithFirstInstallmentDue();
        jdbcTemplate.update("INSERT INTO daily_closing (business_date, daily_closing_status_id, started_at, "
                + "finished_at) VALUES ('2026-10-17', 2, '2026-10-17 00:00:00', '2026-10-17 00:01:00')");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM repayment_allocation");
        jdbcTemplate.update("DELETE FROM repayment");
        jdbcTemplate.update("DELETE FROM loan_status_judgment");
        jdbcTemplate.update("DELETE FROM repayment_schedule");
        jdbcTemplate.update("DELETE FROM loan_account");
        jdbcTemplate.update("DELETE FROM loan_contract");
        jdbcTemplate.update("DELETE FROM loan_product");
        jdbcTemplate.update("DELETE FROM member");
        jdbcTemplate.update("DELETE FROM daily_closing");
        jdbcTemplate.update("DELETE FROM telegram_no_sequence");
    }

    @Nested
    @DisplayName("펌뱅킹이 이체를 끝냈는데 응답이 오지 않을 때")
    class 집금_응답_유실 {

        @Test
        @DisplayName("1회차 1,027,289원을 상환하면 집금 요청은 1번 나갔는데 집금 실패로 끝나고 납부 기록 0건, 원금잔액 12,000,000원 그대로다")
        void 이체는_됐는데_상환은_반영되지_않는다() {
            // given
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                    .willThrow(responseTimeout());

            // when, then
            assertThatThrownBy(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER))
                    .isInstanceOf(CollectionFailedException.class);
            then(firmbankingClient).should(times(1))
                    .requestCollection(any(), anyInt(), anyString(), anyString(), any());
            assertThat(countRepayments()).isZero();
            assertThat(findPrincipalBalance()).isEqualByComparingTo(PRINCIPAL_BALANCE);
        }

        @Test
        @DisplayName("실패 응답을 받은 고객이 같은 금액을 다시 상환하면 성공하지만 집금 요청이 전문번호 1, 2로 2번 나가고 납부 기록은 1건이다")
        void 다시_상환하면_돈이_두_번_빠진다() {
            // given
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                    .willThrow(responseTimeout())
                    .willReturn(SUCCESS);
            Throwable firstAttempt = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));
            assertThat(firstAttempt).isInstanceOf(CollectionFailedException.class);

            // when
            RepaymentResultResponse result =
                    repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT, RepaymentChannel.CUSTOMER);

            // then
            assertThat(result.getPaidAmount()).isEqualByComparingTo(FIRST_INSTALLMENT);
            ArgumentCaptor<Integer> telegramNos = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            then(firmbankingClient).should(times(2))
                    .requestCollection(any(), telegramNos.capture(), anyString(), anyString(), amounts.capture());
            assertThat(telegramNos.getAllValues()).containsExactly(1, 2);
            assertThat(amounts.getAllValues())
                    .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(FIRST_INSTALLMENT));
            assertThat(countRepayments()).isEqualTo(1);
        }
    }

    private static UncheckedIOException responseTimeout() {
        return new UncheckedIOException(new SocketTimeoutException("Read timed out"));
    }

    // 고정금리·원리금균등·1,200만·연 5%·12회. 1회차 예정일이 오늘 10/17이고 일마감이 확정해 납부 기한 도래다.
    private void insertLoanWithFirstInstallmentDue() {
        jdbcTemplate.update("INSERT INTO member (member_id, login_id, password, name) "
                + "VALUES (1, 'kimcs', 'password', '김철수')");
        jdbcTemplate.update("INSERT INTO loan_product (loan_product_id, interest_type_id, repayment_method_id, name, "
                + "additional_rate) VALUES (1, 1, 2, '고정금리 원리금균등', 2.00000)");
        jdbcTemplate.update("INSERT INTO loan_contract (loan_contract_id, loan_product_id, member_id, contract_date, "
                + "maturity_date, contract_principal, contract_additional_rate, contract_base_rate, "
                + "total_installments) VALUES (1, 1, 1, '2026-09-17', '2027-09-17', 12000000, 2.00000, 3.00000, 12)");
        jdbcTemplate.update("INSERT INTO loan_account (loan_account_id, loan_contract_id, repayment_bank_code, "
                + "repayment_account_no, account_no, loan_status_id, loan_principal_balance, monthly_payment_day) "
                + "VALUES (?, 1, '999', '200-000-000001', '900-000-000001', 1, ?, 17)",
                LOAN_ACCOUNT_ID, PRINCIPAL_BALANCE);
        jdbcTemplate.update("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date, scheduled_principal, scheduled_interest, "
                + "applied_base_rate, applied_additional_rate, confirmed_at) "
                + "VALUES (?, 1, 2, '2026-10-17', 977289, 50000, 3.00000, 2.00000, '2026-10-17 00:00:30')",
                LOAN_ACCOUNT_ID);
        jdbcTemplate.update("INSERT INTO repayment_schedule (loan_account_id, installment_no, "
                + "repayment_schedule_status_id, scheduled_date) "
                + "WITH RECURSIVE installment (installment_no) AS ("
                + "    SELECT 2 UNION ALL SELECT installment_no + 1 FROM installment WHERE installment_no < 12) "
                + "SELECT ?, installment_no, 1, DATE_ADD('2026-09-17', INTERVAL installment_no MONTH) "
                + "FROM installment", LOAN_ACCOUNT_ID);
    }

    private int countRepayments() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM repayment WHERE loan_account_id = ?",
                Integer.class, LOAN_ACCOUNT_ID);
    }

    private BigDecimal findPrincipalBalance() {
        return jdbcTemplate.queryForObject("SELECT loan_principal_balance FROM loan_account WHERE loan_account_id = ?",
                BigDecimal.class, LOAN_ACCOUNT_ID);
    }
}
