package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.exception.CollectionFailedException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.WithdrawalAccountNotFoundException;
import com.repayment.domain.repayment.exception.WithdrawalConsentNotRegisteredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상환 계좌에서 수납 계좌로 집금을 요청하고, 0000이 아니면 응답코드에 맞는 예외를 던진다.
 * 고객이 고칠 수 있는 코드(잔액 부족·출금 동의 없음·출금 계좌 없음)만 따로 던지고, 나머지는 집금 실패로 묶는다.
 * 응답을 받지 못해도 집금 실패로 본다.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String SUCCESS = "0000";
    private static final String WITHDRAWAL_ACCOUNT_NOT_FOUND = "B001";
    private static final String INSUFFICIENT_BALANCE = "B003";
    private static final String CONSENT_NOT_REGISTERED = "C001";

    private final FirmbankingClient firmbankingClient;

    /**
     * transactionNo는 예외에 담아 원인을 추적하는 데만 쓴다.
     */
    public void collect(String transactionNo, LocalDateTime sentAt, int telegramNo, String withdrawalBankCode,
                        String withdrawalAccountNo, BigDecimal amount) {
        String responseCode = requestCollection(transactionNo, sentAt, telegramNo, withdrawalBankCode,
                withdrawalAccountNo, amount);
        switch (responseCode) {
            case SUCCESS -> {
            }
            case WITHDRAWAL_ACCOUNT_NOT_FOUND -> throw new WithdrawalAccountNotFoundException(transactionNo);
            case INSUFFICIENT_BALANCE -> throw new InsufficientBalanceException(transactionNo);
            case CONSENT_NOT_REGISTERED -> throw new WithdrawalConsentNotRegisteredException(transactionNo);
            default -> throw new CollectionFailedException(transactionNo, responseCode);
        }
    }

    private String requestCollection(String transactionNo, LocalDateTime sentAt, int telegramNo,
                                     String withdrawalBankCode, String withdrawalAccountNo, BigDecimal amount) {
        try {
            return firmbankingClient.requestCollection(sentAt, telegramNo, withdrawalBankCode, withdrawalAccountNo,
                    amount);
        } catch (UncheckedIOException e) {
            throw new CollectionFailedException(transactionNo, e);
        }
    }
}
