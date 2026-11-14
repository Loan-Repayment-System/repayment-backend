package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.client.FirmbankingClient;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.exception.CollectionFailedException;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.WithdrawalAccountNotFoundException;
import com.repayment.domain.repayment.exception.WithdrawalConsentNotRegisteredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.LocalDate;

/**
 * 저장한 집금 요청 그대로 펌뱅킹에 집금을 보내고, 0000이 아니면 응답코드에 맞는 거절 예외를 던진다.
 * 고객이 고칠 수 있는 코드(잔액 부족·출금 동의 없음·출금 계좌 없음)만 따로 던지고, 나머지는 집금 실패로 묶는다.
 * 응답을 받지 못하면 펌뱅킹이 이체했는지 알 수 없으므로 거절로 보지 않고 결과 확인 중 예외를 던진다.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String SUCCESS = "0000";
    private static final String WITHDRAWAL_ACCOUNT_NOT_FOUND = "B001";
    private static final String INSUFFICIENT_BALANCE = "B003";
    private static final String CONSENT_NOT_REGISTERED = "C001";

    private final FirmbankingClient firmbankingClient;

    public void collect(CollectionRequestResponse request) {
        String responseCode = requestCollection(request);
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

    private String requestCollection(CollectionRequestResponse request) {
        try {
            return firmbankingClient.requestCollection(request.getSentAt(), request.getTelegramNo(),
                    request.getWithdrawalBankCode(), request.getWithdrawalAccountNo(), request.getAmount());
        } catch (UncheckedIOException e) {
            throw new CollectionResultPendingException(request.getTransactionNo(), e);
        }
    }
}
