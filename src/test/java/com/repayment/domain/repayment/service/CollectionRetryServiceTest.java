package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("집금 요청 재요청 1건 처리")
class CollectionRetryServiceTest {

    private static final long COLLECTION_REQUEST_ID = 300L;
    private static final LocalDate SEND_DATE = LocalDate.of(2026, 10, 17);
    private static final int TELEGRAM_NO = 1;
    private static final String TRANSACTION_NO = "20261017000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0);

    @Mock
    private CollectionRequestService collectionRequestService;

    @Mock
    private CollectionService collectionService;

    @Mock
    private RepaymentReflectionService repaymentReflectionService;

    @InjectMocks
    private CollectionRetryService collectionRetryService;

    @Nested
    @DisplayName("요청 상태일 때")
    class 요청_상태 {

        private final CollectionRequestResponse request = request(CollectionRequestStatus.REQUESTED);

        @Test
        @DisplayName("집금에 성공하면 집금 완료로 바꾼 뒤 상환에 반영하고, 시도를 기록하지 않는다")
        void 집금에_성공하면_반영한다() {
            // when
            collectionRetryService.retry(request, NOW);

            // then
            InOrder inOrder = inOrder(collectionService, collectionRequestService, repaymentReflectionService);
            then(collectionService).should(inOrder).collect(request);
            then(collectionRequestService).should(inOrder).markCollected(COLLECTION_REQUEST_ID);
            then(repaymentReflectionService).should(inOrder).reflect(request);
            then(collectionRequestService).should(never()).recordRetryAttempt(any(), any());
        }

        @Test
        @DisplayName("집금에 성공하고 반영에 실패하면 집금 완료로 바꾸고 시도를 기록한다")
        void 집금_성공_후_반영에_실패하면_시도를_기록한다() {
            // given
            willThrow(new IllegalStateException("반영 실패")).given(repaymentReflectionService).reflect(request);

            // when
            collectionRetryService.retry(request, NOW);

            // then
            then(collectionRequestService).should().markCollected(COLLECTION_REQUEST_ID);
            then(collectionRequestService).should().recordRetryAttempt(request, NOW);
        }

        @Test
        @DisplayName("잔액 부족으로 거절되면 거절 상태로 바꾸고, 반영하지 않고 시도를 기록하지 않는다")
        void 거절되면_거절_상태로_바꾼다() {
            // given
            willThrow(new InsufficientBalanceException(SEND_DATE, TELEGRAM_NO))
                    .given(collectionService).collect(request);

            // when
            collectionRetryService.retry(request, NOW);

            // then
            then(collectionRequestService).should().markFailed(COLLECTION_REQUEST_ID);
            then(repaymentReflectionService).shouldHaveNoInteractions();
            then(collectionRequestService).should(never()).recordRetryAttempt(any(), any());
        }

        @Test
        @DisplayName("응답이 없으면 상태를 바꾸지 않고 반영하지 않으며 시도를 기록한다")
        void 응답이_없으면_시도를_기록한다() {
            // given
            willThrow(new CollectionResultPendingException(TRANSACTION_NO,
                    new UncheckedIOException(new SocketTimeoutException("Read timed out"))))
                    .given(collectionService).collect(request);

            // when
            collectionRetryService.retry(request, NOW);

            // then
            then(collectionRequestService).should().recordRetryAttempt(request, NOW);
            then(collectionRequestService).should(never()).markCollected(anyLong());
            then(collectionRequestService).should(never()).markFailed(anyLong());
            then(repaymentReflectionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("집금 완료 상태일 때")
    class 집금_완료_상태 {

        private final CollectionRequestResponse request = request(CollectionRequestStatus.COLLECTED);

        @Test
        @DisplayName("집금은 보내지 않고 상환에 반영하며, 반영에 성공하면 시도를 기록하지 않는다")
        void 집금_없이_반영한다() {
            // when
            collectionRetryService.retry(request, NOW);

            // then
            then(repaymentReflectionService).should().reflect(request);
            then(collectionService).shouldHaveNoInteractions();
            then(collectionRequestService).should(never()).recordRetryAttempt(any(), any());
        }

        @Test
        @DisplayName("반영에 실패하면 집금은 보내지 않고 시도를 기록한다")
        void 반영에_실패하면_시도를_기록한다() {
            // given
            willThrow(new IllegalStateException("반영 실패")).given(repaymentReflectionService).reflect(request);

            // when
            collectionRetryService.retry(request, NOW);

            // then
            then(collectionService).shouldHaveNoInteractions();
            then(collectionRequestService).should().recordRetryAttempt(request, NOW);
        }
    }

    private static CollectionRequestResponse request(CollectionRequestStatus status) {
        return CollectionRequestResponse.builder()
                .collectionRequestId(COLLECTION_REQUEST_ID)
                .loanAccountId(10L)
                .repaymentChannelId(RepaymentChannel.CUSTOMER)
                .collectionRequestStatusId(status)
                .sendDate(SEND_DATE)
                .telegramNo(TELEGRAM_NO)
                .sentAt(LocalDateTime.of(2026, 10, 17, 9, 59))
                .withdrawalBankCode("999")
                .withdrawalAccountNo("200-000-000001")
                .amount(new BigDecimal("30000"))
                .retryCount(0)
                .nextRetryAt(NOW)
                .build();
    }
}
