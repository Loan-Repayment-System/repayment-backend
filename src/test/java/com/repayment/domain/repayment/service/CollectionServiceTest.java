package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepaymentPendingResponse;
import com.repayment.domain.repayment.exception.CollectionFailedException;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.WithdrawalAccountNotFoundException;
import com.repayment.domain.repayment.exception.WithdrawalConsentNotRegisteredException;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.exception.CollectionRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("집금")
class CollectionServiceTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 16, 14, 30, 15);
    private static final int TELEGRAM_NO = 1;
    private static final String BANK_CODE = "999";
    private static final String ACCOUNT_NO = "200-000-000001";
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final CollectionRequestResponse REQUEST = CollectionRequestResponse.builder()
            .collectionRequestId(1L)
            .loanAccountId(10L)
            .repaymentChannelId(RepaymentChannel.CUSTOMER)
            .collectionRequestStatusId(CollectionRequestStatus.REQUESTED)
            .sendDate(LocalDate.of(2026, 10, 16))
            .telegramNo(TELEGRAM_NO)
            .sentAt(SENT_AT)
            .withdrawalBankCode(BANK_CODE)
            .withdrawalAccountNo(ACCOUNT_NO)
            .amount(AMOUNT)
            .build();

    @Mock
    private FirmbankingClient firmbankingClient;

    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionService = new CollectionService(firmbankingClient);
    }

    @Nested
    @DisplayName("펌뱅킹이 응답코드를 돌려줄 때")
    class 응답을_받음 {

        @Test
        @DisplayName("0000이면 예외 없이 끝나고, 집금 요청의 시각·전문번호·계좌·금액을 그대로 넘긴다")
        void 성공이면_예외_없이_끝난다() {
            // given
            givenResponseCode("0000");

            // when
            collect();

            // then
            then(firmbankingClient).should().requestCollection(SENT_AT, TELEGRAM_NO, BANK_CODE, ACCOUNT_NO, AMOUNT);
        }

        @Test
        @DisplayName("B003이면 거절 범주의 잔액 부족 예외를 던진다")
        void 잔액_부족이면_잔액_부족_예외를_던진다() {
            // given
            givenResponseCode("B003");

            // when
            Throwable thrown = catchThrowable(() -> collect());

            // then
            assertThat(thrown).isInstanceOf(InsufficientBalanceException.class).isInstanceOf(CollectionRejectedException.class);
        }

        @Test
        @DisplayName("C001이면 거절 범주의 출금 동의 없음 예외를 던진다")
        void 출금_동의가_없으면_출금_동의_없음_예외를_던진다() {
            // given
            givenResponseCode("C001");

            // when
            Throwable thrown = catchThrowable(() -> collect());

            // then
            assertThat(thrown).isInstanceOf(WithdrawalConsentNotRegisteredException.class).isInstanceOf(CollectionRejectedException.class);
        }

        @Test
        @DisplayName("B001이면 거절 범주의 출금 계좌 없음 예외를 던진다")
        void 출금_계좌가_없으면_출금_계좌_없음_예외를_던진다() {
            // given
            givenResponseCode("B001");

            // when
            Throwable thrown = catchThrowable(() -> collect());

            // then
            assertThat(thrown).isInstanceOf(WithdrawalAccountNotFoundException.class).isInstanceOf(CollectionRejectedException.class);
        }

        @Test
        @DisplayName("9999면 응답코드 9999를 담은 거절 범주의 집금 실패 예외를 던진다")
        void 그_밖의_코드면_집금_실패_예외를_던진다() {
            // given
            givenResponseCode("9999");

            // when
            Throwable thrown = catchThrowable(() -> collect());

            // then
            assertThat(thrown).isInstanceOf(CollectionFailedException.class).isInstanceOf(CollectionRejectedException.class);
            assertThat(((CollectionFailedException) thrown).getResponseCode()).isEqualTo("9999");
        }
    }

    @Nested
    @DisplayName("응답을 받지 못할 때")
    class 응답을_받지_못함 {

        @Test
        @DisplayName("응답 시간 초과면 거절로 보지 않고, 원인 예외와 거래번호 20261016000001을 담은 결과 확인 중 예외를 던진다")
        void 응답이_없으면_결과_확인_중_예외를_던진다() {
            // given
            UncheckedIOException timeout =
                    new UncheckedIOException("펌뱅킹 전문 송수신 실패", new SocketTimeoutException("Read timed out"));
            given(firmbankingClient.requestCollection(SENT_AT, TELEGRAM_NO, BANK_CODE, ACCOUNT_NO, AMOUNT))
                    .willThrow(timeout);

            // when
            Throwable thrown = catchThrowable(() -> collect());

            // then
            assertThat(thrown).isInstanceOf(CollectionResultPendingException.class)
                    .isNotInstanceOf(CollectionRejectedException.class)
                    .hasCause(timeout);
            RepaymentPendingResponse data = (RepaymentPendingResponse) ((CollectionResultPendingException) thrown).getData();
            assertThat(data.getTransactionNo()).isEqualTo("20261016000001");
        }
    }

    private void givenResponseCode(String responseCode) {
        given(firmbankingClient.requestCollection(SENT_AT, TELEGRAM_NO, BANK_CODE, ACCOUNT_NO, AMOUNT))
                .willReturn(responseCode);
    }

    private void collect() {
        collectionService.collect(REQUEST);
    }
}
