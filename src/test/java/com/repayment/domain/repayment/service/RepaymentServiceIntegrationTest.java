package com.repayment.domain.repayment.service;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.exception.CollectionRequestNotCollectedException;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentInProgressException;
import com.repayment.domain.repayment.exception.RepaymentReflectionPendingException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

/**
 * 집금 결과(성공·거절·응답 없음)와 상환 반영 실패마다 실제 DB에 무엇이 남는지 검증한다.
 * 펌뱅킹 클라이언트는 목으로 바꾸고, 집금 요청이 클라이언트까지 왔으면 펌뱅킹은 이체를 끝낸 것으로 본다.
 * 상환 처리가 트랜잭션을 나눠 스스로 커밋하므로 @Transactional로 롤백할 수 없다. 테스트마다 행을 지운다.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("상환 처리의 집금 결과별 반영")
class RepaymentServiceIntegrationTest {

    private static final long LOAN_ACCOUNT_ID = 1L;
    private static final BigDecimal PRINCIPAL_BALANCE = new BigDecimal("12000000");
    // 1회차 원금 977,289 + 이자 50,000
    private static final BigDecimal FIRST_INSTALLMENT = new BigDecimal("1027289");
    private static final String SUCCESS = "0000";
    private static final int REQUESTED = 1;
    private static final int COLLECTED = 2;
    private static final int COMPLETED = 3;
    private static final int FAILED = 4;

    @Autowired
    private RepaymentService repaymentService;

    @Autowired
    private RepaymentReflectionService repaymentReflectionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirmbankingClient firmbankingClient;

    @MockitoSpyBean
    private InstallmentAllocationService installmentAllocationService;

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
        jdbcTemplate.update("DELETE FROM collection_request");
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
    @DisplayName("집금에 성공할 때")
    class 집금_성공 {

        @Test
        @DisplayName("1회차 1,027,289원을 상환하면 집금 요청은 상환 완료, 납부 기록 1건, 원금잔액은 977,289원 줄어든 11,022,711원이다")
        void 상환을_반영하고_상환_완료로_바꾼다() {
            // given
            givenResponseCode(SUCCESS);

            // when
            RepaymentResultResponse result =
                    repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT, RepaymentChannel.CUSTOMER);

            // then
            assertThat(result.getLoanPrincipalBalance()).isEqualByComparingTo("11022711");
            assertThat(findRequestStatusIds()).containsExactly(COMPLETED);
            assertThat(countRepayments()).isEqualTo(1);
            assertThat(findPrincipalBalance()).isEqualByComparingTo("11022711");
        }
    }

    @Nested
    @DisplayName("이미 상환 완료된 집금 요청을 다시 반영할 때")
    class 중복_반영 {

        @Test
        @DisplayName("1회차 중 30,000원을 상환한 집금 요청을 다시 반영하면 집금 완료 상태 아님 예외로 롤백되어 납부 기록 1건, 1회차 낸 이자 30,000원 그대로다")
        void 두_번째_반영을_롤백한다() {
            // given
            givenResponseCode(SUCCESS);
            repaymentService.repay(LOAN_ACCOUNT_ID, new BigDecimal("30000"), RepaymentChannel.CUSTOMER);
            CollectionRequestResponse completed = findCollectionRequest();

            // when
            Throwable thrown = catchThrowable(() -> repaymentReflectionService.reflect(completed));

            // then
            assertThat(thrown).isInstanceOf(CollectionRequestNotCollectedException.class);
            assertThat(countRepayments()).isEqualTo(1);
            assertThat(findFirstInstallmentPaidInterest()).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("펌뱅킹이 잔액 부족으로 거절할 때")
    class 집금_거절 {

        @Test
        @DisplayName("1회차 1,027,289원을 상환하면 잔액 부족 예외로 끝나고 집금 요청은 거절, 납부 기록 0건이다")
        void 거절로_남기고_반영하지_않는다() {
            // given
            givenResponseCode("B003");

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(InsufficientBalanceException.class);
            assertThat(findRequestStatusIds()).containsExactly(FAILED);
            assertThat(countRepayments()).isZero();
        }
    }

    @Nested
    @DisplayName("펌뱅킹이 이체를 끝냈는데 응답이 오지 않을 때")
    class 집금_응답_유실 {

        @Test
        @DisplayName("다시 보내도 응답이 없으면 결과 확인 중으로 끝나고, 보낸 내용(10/17, 전문번호 1, 999 / 200-000-000001, 1,027,289원)이 요청 상태로 남으며 납부 기록 0건, 원금잔액 12,000,000원 그대로다")
        void 보낸_내용을_요청_상태로_남긴다() {
            // given
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                    .willThrow(responseTimeout());

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(CollectionResultPendingException.class);
            List<Map<String, Object>> requests = jdbcTemplate.queryForList("SELECT collection_request_status_id, "
                    + "send_date, telegram_no, withdrawal_bank_code, withdrawal_account_no, amount "
                    + "FROM collection_request WHERE loan_account_id = ?", LOAN_ACCOUNT_ID);
            assertThat(requests).hasSize(1);
            Map<String, Object> request = requests.get(0);
            assertThat(((Number) request.get("collection_request_status_id")).intValue()).isEqualTo(REQUESTED);
            assertThat(request.get("send_date").toString()).isEqualTo("2026-10-17");
            assertThat(((Number) request.get("telegram_no")).intValue()).isEqualTo(1);
            assertThat(request.get("withdrawal_bank_code")).isEqualTo("999");
            assertThat(request.get("withdrawal_account_no")).isEqualTo("200-000-000001");
            assertThat((BigDecimal) request.get("amount")).isEqualByComparingTo(FIRST_INSTALLMENT);
            assertThat(countRepayments()).isZero();
            assertThat(findPrincipalBalance()).isEqualByComparingTo(PRINCIPAL_BALANCE);
        }

        @Test
        @DisplayName("다시 보낸 요청에 0000을 받으면 1회차 1,027,289원 상환에 성공하고, 집금 요청은 상환 완료 1건, 납부 기록 1건이다")
        void 다시_보내서_성공하면_상환을_반영한다() {
            // given
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                    .willThrow(responseTimeout())
                    .willReturn(SUCCESS);

            // when
            RepaymentResultResponse result =
                    repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT, RepaymentChannel.CUSTOMER);

            // then
            assertThat(result.getPaidAmount()).isEqualByComparingTo(FIRST_INSTALLMENT);
            assertThat(findRequestStatusIds()).containsExactly(COMPLETED);
            assertThat(countRepayments()).isEqualTo(1);
        }

        @Test
        @DisplayName("결과 확인 중 응답을 받은 고객이 같은 금액을 다시 상환하면 처리 중인 상환 있음으로 거절되고, 집금은 전문번호 1로 2번만 나가 요청 1건이 남는다")
        void 다시_상환하면_처리_중인_상환_있음으로_거절한다() {
            // given
            given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                    .willThrow(responseTimeout());
            Throwable firstAttempt = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));
            assertThat(firstAttempt).isInstanceOf(CollectionResultPendingException.class);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(RepaymentInProgressException.class);
            ArgumentCaptor<Integer> telegramNos = ArgumentCaptor.forClass(Integer.class);
            then(firmbankingClient).should(times(2))
                    .requestCollection(any(), telegramNos.capture(), anyString(), anyString(), any());
            assertThat(telegramNos.getAllValues()).containsExactly(1, 1);
            assertThat(findRequestStatusIds()).containsExactly(REQUESTED);
            assertThat(countRepayments()).isZero();
        }
    }

    @Nested
    @DisplayName("집금에 성공했는데 상환 반영이 실패할 때")
    class 반영_실패 {

        @Test
        @DisplayName("충당이 예외를 던지면 반영 중으로 끝나고, 반영만 롤백되어 집금 요청은 집금 완료로 남으며 납부 기록 0건, 원금잔액 12,000,000원 그대로다")
        void 집금_완료로_남기고_반영은_롤백한다() {
            // given
            givenResponseCode(SUCCESS);
            willThrow(new RepaymentAmountLeftOverException(LOAN_ACCOUNT_ID, BigDecimal.ONE))
                    .given(installmentAllocationService).allocate(anyLong(), anyLong(), any(), anyList());

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(RepaymentReflectionPendingException.class);
            assertThat(findRequestStatusIds()).containsExactly(COLLECTED);
            assertThat(countRepayments()).isZero();
            assertThat(findPrincipalBalance()).isEqualByComparingTo(PRINCIPAL_BALANCE);
        }

        @Test
        @DisplayName("반영 중 응답을 받은 고객이 같은 금액을 다시 상환하면 처리 중인 상환 있음으로 거절되고, 집금은 1번만 나간다")
        void 다시_상환하면_처리_중인_상환_있음으로_거절한다() {
            // given
            givenResponseCode(SUCCESS);
            willThrow(new RepaymentAmountLeftOverException(LOAN_ACCOUNT_ID, BigDecimal.ONE))
                    .given(installmentAllocationService).allocate(anyLong(), anyLong(), any(), anyList());
            Throwable firstAttempt = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));
            assertThat(firstAttempt).isInstanceOf(RepaymentReflectionPendingException.class);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, FIRST_INSTALLMENT,
                    RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(RepaymentInProgressException.class);
            then(firmbankingClient).should(times(1))
                    .requestCollection(any(), anyInt(), anyString(), anyString(), any());
            assertThat(findRequestStatusIds()).containsExactly(COLLECTED);
        }
    }

    private void givenResponseCode(String responseCode) {
        given(firmbankingClient.requestCollection(any(), anyInt(), anyString(), anyString(), any()))
                .willReturn(responseCode);
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

    private CollectionRequestResponse findCollectionRequest() {
        return jdbcTemplate.queryForObject("SELECT collection_request_id, loan_account_id, repayment_channel_id, "
                + "send_date, telegram_no, sent_at, amount FROM collection_request WHERE loan_account_id = ?",
                (rs, rowNum) -> CollectionRequestResponse.builder()
                        .collectionRequestId(rs.getLong("collection_request_id"))
                        .loanAccountId(rs.getLong("loan_account_id"))
                        .repaymentChannelId(RepaymentChannel.CUSTOMER)
                        .sendDate(rs.getObject("send_date", LocalDate.class))
                        .telegramNo(rs.getInt("telegram_no"))
                        .sentAt(rs.getObject("sent_at", LocalDateTime.class))
                        .amount(rs.getBigDecimal("amount"))
                        .build(),
                LOAN_ACCOUNT_ID);
    }

    private List<Integer> findRequestStatusIds() {
        return jdbcTemplate.queryForList("SELECT collection_request_status_id FROM collection_request "
                + "WHERE loan_account_id = ? ORDER BY telegram_no", Integer.class, LOAN_ACCOUNT_ID);
    }

    private int countRepayments() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM repayment WHERE loan_account_id = ?",
                Integer.class, LOAN_ACCOUNT_ID);
    }

    private BigDecimal findFirstInstallmentPaidInterest() {
        return jdbcTemplate.queryForObject("SELECT paid_interest FROM repayment_schedule "
                + "WHERE loan_account_id = ? AND installment_no = 1", BigDecimal.class, LOAN_ACCOUNT_ID);
    }

    private BigDecimal findPrincipalBalance() {
        return jdbcTemplate.queryForObject("SELECT loan_principal_balance FROM loan_account WHERE loan_account_id = ?",
                BigDecimal.class, LOAN_ACCOUNT_ID);
    }
}
