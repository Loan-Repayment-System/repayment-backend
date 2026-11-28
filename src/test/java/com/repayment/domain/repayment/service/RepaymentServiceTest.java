package com.repayment.domain.repayment.service;

import com.repayment.domain.loan.dto.response.LoanAccountStatusResponse;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentPendingResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.NothingToRepayException;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentInProgressException;
import com.repayment.domain.repayment.exception.RepaymentReflectionPendingException;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.util.TransientDatabaseErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("상환 처리")
class RepaymentServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long COLLECTION_REQUEST_ID = 300L;
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 30, 15, 789_000_000);
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 16, 14, 30, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final int TELEGRAM_NO = 1;
    private static final String TRANSACTION_NO = "20261016000001";
    private static final CollectionRequestResponse REQUEST = CollectionRequestResponse.builder()
            .collectionRequestId(COLLECTION_REQUEST_ID)
            .loanAccountId(LOAN_ACCOUNT_ID)
            .repaymentChannelId(RepaymentChannel.CUSTOMER)
            .collectionRequestStatusId(CollectionRequestStatus.REQUESTED)
            .sendDate(TODAY)
            .telegramNo(TELEGRAM_NO)
            .sentAt(SENT_AT)
            .withdrawalBankCode("999")
            .withdrawalAccountNo("200-000-000001")
            .amount(AMOUNT)
            .build();

    @Mock
    private CollectionRequestService collectionRequestService;

    @Mock
    private CollectionService collectionService;

    @Mock
    private RepaymentReflectionService repaymentReflectionService;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private RepayableAmountService repayableAmountService;

    private RepaymentService repaymentService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        // 운영과 같은 정책에서 시도 사이 대기만 뺀다.
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .noBackoff()
                .retryOn(TransientDatabaseErrors::matches)
                .build();
        repaymentService = new RepaymentService(collectionRequestService, collectionService, repaymentReflectionService,
                loanAccountMapper, loanStatusJudgmentMapper, repayableAmountService, retryTemplate, clock);
    }

    @Nested
    @DisplayName("집금에 성공할 때")
    class 집금_성공 {

        @BeforeEach
        void setUp() {
            givenRequestCreated();
        }

        @Test
        @DisplayName("집금 요청 저장 → 집금 → 집금 완료 표시 → 상환 반영 → 결과 조회 순서로 부른다")
        void 요청을_저장하고_집금한_뒤_반영한다() {
            // given
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "10000");

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            InOrder inOrder = inOrder(collectionRequestService, collectionService, repaymentReflectionService,
                    loanAccountMapper, repayableAmountService);
            then(collectionRequestService).should(inOrder)
                    .create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            then(collectionService).should(inOrder).collectWithImmediateRetry(REQUEST);
            then(collectionRequestService).should(inOrder).markCollected(COLLECTION_REQUEST_ID);
            then(repaymentReflectionService).should(inOrder).reflect(REQUEST);
            then(loanAccountMapper).should(inOrder).findLoanAccountStatus(LOAN_ACCOUNT_ID);
            then(repayableAmountService).should(inOrder).calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
        }

        @Test
        @DisplayName("상환 시각이 14:30:15.789면 집금 요청과 응답의 시각은 초 미만을 버린 14:30:15다")
        void 상환_시각은_초_미만을_버린다() {
            // given
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "10000");

            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            then(collectionRequestService).should().create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            assertThat(result.getPaidAt()).isEqualTo(SENT_AT);
        }

        @Test
        @DisplayName("상환 뒤 원금잔액 9,000,000원·정상·갚을 금액 10,000원이면 30,000원, 14:30:15, 9,000,000원, NORMAL, 10,000원을 돌려준다")
        void 상환_뒤_상태로_결과를_채운다() {
            // given
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "10000");

            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            assertThat(result.getPaidAmount()).isEqualByComparingTo("30000");
            assertThat(result.getPaidAt()).isEqualTo(SENT_AT);
            assertThat(result.getLoanPrincipalBalance()).isEqualByComparingTo("9000000");
            assertThat(result.getLoanStatus()).isEqualTo(LoanStatus.NORMAL);
            assertThat(result.getRemainingRepayableAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("상환 뒤 판정이 2 완제면 완제 판정으로 갚을 금액을 계산해 남은 갚을 금액 0원, 원금잔액 0원, PAID_OFF를 돌려준다")
        void 상환_뒤_판정으로_남은_금액을_계산한다() {
            // given
            givenResult("0", LoanStatus.PAID_OFF, LoanJudgmentStatus.PAID_OFF, "0");

            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            then(repayableAmountService).should().calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.PAID_OFF);
            assertThat(result.getRemainingRepayableAmount()).isEqualByComparingTo("0");
            assertThat(result.getLoanPrincipalBalance()).isEqualByComparingTo("0");
            assertThat(result.getLoanStatus()).isEqualTo(LoanStatus.PAID_OFF);
        }
    }

    @Nested
    @DisplayName("집금 결과가 성공이 아닐 때")
    class 집금_실패 {

        @BeforeEach
        void setUp() {
            givenRequestCreated();
        }

        @Test
        @DisplayName("잔액 부족으로 거절되면 거절로 바꾸고 같은 예외를 던지며, 집금 완료 표시와 반영은 부르지 않는다")
        void 거절되면_거절로_바꾸고_반영하지_않는다() {
            // given
            InsufficientBalanceException rejected = new InsufficientBalanceException(TODAY, TELEGRAM_NO);
            willThrow(rejected).given(collectionService).collectWithImmediateRetry(REQUEST);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(rejected);
            then(collectionRequestService).should().markFailed(COLLECTION_REQUEST_ID);
            then(collectionRequestService).should(never()).markCollected(anyLong());
            then(repaymentReflectionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("응답을 받지 못하면 상태를 바꾸지 않고 결과 확인 중 예외를 그대로 던지며, 반영은 부르지 않는다")
        void 응답이_없으면_요청_상태로_두고_반영하지_않는다() {
            // given
            CollectionResultPendingException pending = new CollectionResultPendingException(TRANSACTION_NO,
                    new UncheckedIOException(new SocketTimeoutException("Read timed out")));
            willThrow(pending).given(collectionService).collectWithImmediateRetry(REQUEST);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(pending);
            then(collectionRequestService).should(never()).markFailed(anyLong());
            then(collectionRequestService).should(never()).markCollected(anyLong());
            then(repaymentReflectionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("집금에 성공했는데 상환 반영이 실패할 때")
    class 반영_실패 {

        @Test
        @DisplayName("반영이 충당 잔여 예외를 던지면 그 예외를 원인으로, 거래번호 20261016000001을 data로 담은 반영 중 예외를 던진다")
        void 반영에_실패하면_반영_중_예외를_던진다() {
            // given
            givenRequestCreated();
            RepaymentAmountLeftOverException failed =
                    new RepaymentAmountLeftOverException(LOAN_ACCOUNT_ID, new BigDecimal("1000"));
            willThrow(failed).given(repaymentReflectionService).reflect(REQUEST);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isInstanceOf(RepaymentReflectionPendingException.class).hasCause(failed);
            RepaymentPendingResponse data = (RepaymentPendingResponse) ((RepaymentReflectionPendingException) thrown).getData();
            assertThat(data.getTransactionNo()).isEqualTo(TRANSACTION_NO);
            then(collectionRequestService).should().markCollected(COLLECTION_REQUEST_ID);
        }
    }

    @Nested
    @DisplayName("집금 요청을 저장하기 전에 거절될 때")
    class 요청_전_거절 {

        @Test
        @DisplayName("상환 가능 확인이 갚을 금액 없음으로 거절하면 같은 예외를 던지고 집금·반영은 부르지 않는다")
        void 확인에서_거절되면_집금하지_않는다() {
            // given
            NothingToRepayException rejected = new NothingToRepayException(LOAN_ACCOUNT_ID);
            given(collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT))
                    .willThrow(rejected);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(rejected);
            then(collectionService).shouldHaveNoInteractions();
            then(repaymentReflectionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("상환 진행 중으로 거절하면 다시 해도 같은 결과라 집금 요청 저장을 1번만 시도하고 같은 예외를 던진다")
        void 업무_거절은_다시_시도하지_않는다() {
            // given
            RepaymentInProgressException rejected = new RepaymentInProgressException(LOAN_ACCOUNT_ID);
            given(collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT))
                    .willThrow(rejected);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(rejected);
            then(collectionRequestService).should(times(1))
                    .create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            then(collectionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("집금 요청 저장이 일시적 DB 오류로 실패할 때")
    class 요청_저장_일시_오류 {

        @Test
        @DisplayName("첫 번째에 잠금 획득 실패, 두 번째에 저장되면 저장을 2번 시도하고 집금·반영은 1번씩 부른다")
        void 다시_시도해_저장되면_상환까지_이어진다() {
            // given
            given(collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT))
                    .willThrow(new CannotAcquireLockException("NOWAIT"))
                    .willReturn(REQUEST);
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "10000");

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            then(collectionRequestService).should(times(2))
                    .create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            then(collectionService).should(times(1)).collectWithImmediateRetry(REQUEST);
            then(repaymentReflectionService).should(times(1)).reflect(REQUEST);
        }

        @Test
        @DisplayName("3번 모두 커넥션 획득 실패면 저장을 3번에서 멈추고 마지막 예외를 그대로 던지며 집금은 부르지 않는다")
        void 세_번_모두_실패하면_마지막_예외를_던진다() {
            // given
            CannotCreateTransactionException last = new CannotCreateTransactionException("third");
            given(collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT))
                    .willThrow(new CannotCreateTransactionException("first"),
                            new CannotCreateTransactionException("second"), last);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(last);
            then(collectionRequestService).should(times(3))
                    .create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            then(collectionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("집금 요청을 저장한 뒤 일시적 DB 오류가 날 때")
    class 요청_저장_뒤_일시_오류 {

        @Test
        @DisplayName("집금 완료로 바꾸다 실패하면 집금이 끝난 건이라 저장을 다시 시도하지 않고 1번만 부른다")
        void 저장_뒤_단계는_다시_시도하지_않는다() {
            // given
            givenRequestCreated();
            TransientDataAccessResourceException failed = new TransientDataAccessResourceException("connection lost");
            willThrow(failed).given(collectionRequestService).markCollected(COLLECTION_REQUEST_ID);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(failed);
            then(collectionRequestService).should(times(1))
                    .create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
            then(collectionService).should(times(1)).collectWithImmediateRetry(REQUEST);
        }
    }

    private void givenRequestCreated() {
        given(collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT))
                .willReturn(REQUEST);
    }

    // 상환을 마친 뒤 읽는 원금잔액·대출 상태와, 상환 뒤 판정으로 계산한 갚을 금액
    private void givenResult(String principalBalance, LoanStatus loanStatus, LoanJudgmentStatus latestJudgment,
                             String repayableAmount) {
        given(loanAccountMapper.findLoanAccountStatus(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(new LoanAccountStatusResponse(new BigDecimal(principalBalance), loanStatus)));
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID)).willReturn(Optional.of(latestJudgment));
        given(repayableAmountService.calculate(LOAN_ACCOUNT_ID, latestJudgment))
                .willReturn(new RepayableAmountResponse(new BigDecimal(repayableAmount), List.of()));
    }
}
