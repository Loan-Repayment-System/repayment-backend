package com.repayment.domain.repayment.client;

import com.repayment.domain.repayment.client.codec.CommonField;
import com.repayment.domain.repayment.client.codec.FieldDecoder;
import com.repayment.domain.repayment.client.codec.FieldEncoder;
import com.repayment.domain.repayment.client.codec.TelegramField;
import com.repayment.domain.repayment.client.field.CollectionField;
import com.repayment.global.config.FirmbankingProperties;
import com.repayment.global.config.RepaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * 펌뱅킹에 집금이체(2000/200) 전문을 보내 고객 상환 계좌의 돈을 캐피탈 수납 계좌로 옮긴다.
 * 응답을 받으면 응답코드를 그대로 돌려준다. 응답을 받지 못하면 FirmbankingTelegramSender의 예외가 그대로 나간다.
 */
@Component
@RequiredArgsConstructor
public class FirmbankingClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private static final String MEMO = "상환금";

    private final FirmbankingTelegramSender firmbankingTelegramSender;
    private final FirmbankingProperties firmbankingProperties;
    private final RepaymentProperties repaymentProperties;

    /**
     * 출금 계좌(고객 상환 계좌)에서 수납 계좌로 amount를 옮긴다.
     * 펌뱅킹은 (업체번호, sentAt의 날짜, telegramNo)가 같으면 같은 거래로 본다.
     */
    public String collect(LocalDateTime sentAt, int telegramNo, String withdrawalBankCode,
                          String withdrawalAccountNo, BigDecimal amount) {
        byte[] request = collectionTelegram(sentAt, telegramNo, withdrawalBankCode, withdrawalAccountNo, amount);
        byte[] response = firmbankingTelegramSender.send(request);
        return FieldDecoder.readAN(response, CommonField.RESPONSE_CODE);
    }

    private byte[] collectionTelegram(LocalDateTime sentAt, int telegramNo, String withdrawalBankCode,
                                      String withdrawalAccountNo, BigDecimal amount) {
        byte[] bytes = new byte[TelegramField.TELEGRAM_LENGTH];
        Arrays.fill(bytes, (byte) ' ');
        FieldEncoder.writeAN(bytes, CommonField.COMPANY_NO, firmbankingProperties.companyNo());
        FieldEncoder.writeN(bytes, CommonField.BANK_CODE, Long.parseLong(withdrawalBankCode));
        FieldEncoder.writeN(bytes, CommonField.TELEGRAM_TYPE_CODE, 2000);
        FieldEncoder.writeN(bytes, CommonField.BUSINESS_TYPE_CODE, 200);
        FieldEncoder.writeN(bytes, CommonField.SEND_COUNT, 1);
        FieldEncoder.writeN(bytes, CommonField.TELEGRAM_NO, telegramNo);
        FieldEncoder.writeN(bytes, CommonField.SEND_DATE, Long.parseLong(sentAt.format(DATE_FORMAT)));
        FieldEncoder.writeN(bytes, CommonField.SEND_TIME, Long.parseLong(sentAt.format(TIME_FORMAT)));

        FieldEncoder.writeAN(bytes, CollectionField.WITHDRAWAL_ACCOUNT_NO, withdrawalAccountNo);
        FieldEncoder.writeAmount(bytes, CollectionField.AMOUNT, amount);
        FieldEncoder.writeAmount(bytes, CollectionField.BALANCE_AFTER, BigDecimal.ZERO);
        FieldEncoder.writeN(bytes, CollectionField.DEPOSIT_BANK_CODE,
                Long.parseLong(repaymentProperties.collectionBankCode()));
        FieldEncoder.writeAN(bytes, CollectionField.DEPOSIT_ACCOUNT_NO, repaymentProperties.collectionAccountNo());
        FieldEncoder.writeN(bytes, CollectionField.FEE, 0);
        FieldEncoder.writeAN(bytes, CollectionField.MEMO, MEMO);
        return bytes;
    }
}
