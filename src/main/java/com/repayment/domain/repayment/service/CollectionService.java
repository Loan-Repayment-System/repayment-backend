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
import java.time.LocalDate;
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

    public void collect(LocalDateTime sentAt, int telegramNo, String withdrawalBankCode, String withdrawalAccountNo,
                        BigDecimal amount) {
        String responseCode = requestCollection(sentAt, telegramNo, withdrawalBankCode, withdrawalAccountNo, amount);
        LocalDate sendDate = sentAt.toLocalDate();
        switch (responseCode) {
            case SUCCESS -> {
            }
            case WITHDRAWAL_ACCOUNT_NOT_FOUND -> throw new WithdrawalAccountNotFoundException(sendDate, telegramNo);
            case INSUFFICIENT_BALANCE -> throw new InsufficientBalanceException(sendDate, telegramNo);
            case CONSENT_NOT_REGISTERED -> throw new WithdrawalConsentNotRegisteredException(sendDate, telegramNo);
            default -> throw new CollectionFailedException(sendDate, telegramNo, responseCode);
        }
    }

    private String requestCollection(LocalDateTime sentAt, int telegramNo, String withdrawalBankCode,
                                     String withdrawalAccountNo, BigDecimal amount) {
        try {
            return firmbankingClient.requestCollection(sentAt, telegramNo, withdrawalBankCode, withdrawalAccountNo,
                    amount);
        } catch (UncheckedIOException e) {
            throw new CollectionFailedException(sentAt.toLocalDate(), telegramNo, e);
        }
    }
}
