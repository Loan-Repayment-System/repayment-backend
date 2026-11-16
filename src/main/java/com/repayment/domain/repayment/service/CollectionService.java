package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.exception.CollectionFailedException;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.WithdrawalAccountNotFoundException;
import com.repayment.domain.repayment.exception.WithdrawalConsentNotRegisteredException;
import com.repayment.global.config.FirmbankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 저장한 집금 요청 그대로 펌뱅킹에 집금을 보내고, 0000이 아니면 응답코드에 맞는 거절 예외를 던진다.
 * 고객이 고칠 수 있는 코드(잔액 부족·출금 동의 없음·출금 계좌 없음)만 따로 던지고, 나머지는 집금 실패로 묶는다.
 * 응답을 받지 못하면 이체했는지 알 수 없으므로 거절로 보지 않고 결과 확인 중 예외를 던진다.
 * 같은 집금 요청을 다시 보내도 펌뱅킹은 저장해 둔 결과를 돌려주므로 두 번 빠지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String SUCCESS = "0000";
    private static final String WITHDRAWAL_ACCOUNT_NOT_FOUND = "B001";
    private static final String INSUFFICIENT_BALANCE = "B003";
    private static final String CONSENT_NOT_REGISTERED = "C001";

    private final FirmbankingClient firmbankingClient;
    private final FirmbankingProperties firmbankingProperties;

    /**
     * 1번만 보낸다. 응답을 받지 못하면 바로 결과 확인 중 예외를 던진다.
     */
    public void collect(CollectionRequestResponse request) {
        handleResponseCode(request, requestCollectionOrThrowPending(request));
    }

    /**
     * 응답을 받지 못하면 잠시 기다렸다가 1번 더 보낸다. 그래도 응답이 없으면 결과 확인 중 예외를 던진다.
     */
    public void collectWithImmediateRetry(CollectionRequestResponse request) {
        String responseCode;
        try {
            responseCode = requestCollection(request);
        } catch (UncheckedIOException e) {
            waitBeforeRetry(request, e);
            responseCode = requestCollectionOrThrowPending(request);
        }
        handleResponseCode(request, responseCode);
    }

    private void handleResponseCode(CollectionRequestResponse request, String responseCode) {
        LocalDate sendDate = request.getSendDate();
        int telegramNo = request.getTelegramNo();
        switch (responseCode) {
            case SUCCESS -> {
            }
            case WITHDRAWAL_ACCOUNT_NOT_FOUND -> throw new WithdrawalAccountNotFoundException(sendDate, telegramNo);
            case INSUFFICIENT_BALANCE -> throw new InsufficientBalanceException(sendDate, telegramNo);
            case CONSENT_NOT_REGISTERED -> throw new WithdrawalConsentNotRegisteredException(sendDate, telegramNo);
            default -> throw new CollectionFailedException(sendDate, telegramNo, responseCode);
        }
    }

    private String requestCollectionOrThrowPending(CollectionRequestResponse request) {
        try {
            return requestCollection(request);
        } catch (UncheckedIOException e) {
            throw new CollectionResultPendingException(request.getTransactionNo(), e);
        }
    }

    private String requestCollection(CollectionRequestResponse request) {
        return firmbankingClient.requestCollection(request.getSentAt(), request.getTelegramNo(),
                request.getWithdrawalBankCode(), request.getWithdrawalAccountNo(), request.getAmount());
    }

    // 여러 요청이 같은 순간에 다시 몰리지 않도록 기본 시간에 0~절반 사이 난수를 더한다.
    private void waitBeforeRetry(CollectionRequestResponse request, UncheckedIOException cause) {
        long delayMillis = firmbankingProperties.retryDelay().toMillis();
        long jitterMillis = ThreadLocalRandom.current().nextLong(delayMillis / 2 + 1);
        try {
            Thread.sleep(delayMillis + jitterMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CollectionResultPendingException(request.getTransactionNo(), cause);
        }
    }
}
