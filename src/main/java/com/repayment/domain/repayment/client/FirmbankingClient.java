package com.repayment.domain.repayment.client;

import com.repayment.global.config.FirmbankingProperties;
import com.repayment.global.config.RepaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    private static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private static final String MEMO = "상환금";

    // 공통부
    private static final Field COMPANY_NO = new Field(9, 12);
    private static final Field BANK_CODE = new Field(21, 3);
    private static final Field TELEGRAM_TYPE_CODE = new Field(24, 4);
    private static final Field BUSINESS_TYPE_CODE = new Field(28, 3);
    private static final Field SEND_COUNT = new Field(31, 1);
    private static final Field TELEGRAM_NO = new Field(32, 6);
    private static final Field SEND_DATE = new Field(38, 8);
    private static final Field SEND_TIME = new Field(46, 6);
    private static final Field RESPONSE_CODE = new Field(52, 4);

    // 집금이체 개별부
    private static final Field WITHDRAWAL_ACCOUNT_NO = new Field(100, 15);
    private static final Field AMOUNT = new Field(129, 13);
    private static final Field BALANCE_AFTER = new Field(143, 13);
    private static final Field DEPOSIT_BANK_CODE = new Field(156, 3);
    private static final Field DEPOSIT_ACCOUNT_NO = new Field(159, 15);
    private static final Field FEE = new Field(174, 9);
    private static final Field MEMO_FIELD = new Field(183, 14);

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
        return readAN(response, RESPONSE_CODE);
    }

    private byte[] collectionTelegram(LocalDateTime sentAt, int telegramNo, String withdrawalBankCode,
                                      String withdrawalAccountNo, BigDecimal amount) {
        byte[] bytes = new byte[FirmbankingTelegramSender.TELEGRAM_LENGTH];
        Arrays.fill(bytes, (byte) ' ');
        writeAN(bytes, COMPANY_NO, firmbankingProperties.companyNo());
        writeN(bytes, BANK_CODE, withdrawalBankCode);
        writeN(bytes, TELEGRAM_TYPE_CODE, "2000");
        writeN(bytes, BUSINESS_TYPE_CODE, "200");
        writeN(bytes, SEND_COUNT, "1");
        writeN(bytes, TELEGRAM_NO, Integer.toString(telegramNo));
        writeN(bytes, SEND_DATE, sentAt.format(DATE_FORMAT));
        writeN(bytes, SEND_TIME, sentAt.format(TIME_FORMAT));

        writeAN(bytes, WITHDRAWAL_ACCOUNT_NO, withdrawalAccountNo);
        writeN(bytes, AMOUNT, amount.toBigIntegerExact().toString());
        writeN(bytes, BALANCE_AFTER, "0");
        writeN(bytes, DEPOSIT_BANK_CODE, repaymentProperties.collectionBankCode());
        writeAN(bytes, DEPOSIT_ACCOUNT_NO, repaymentProperties.collectionAccountNo());
        writeN(bytes, FEE, "0");
        writeAN(bytes, MEMO_FIELD, MEMO);
        return bytes;
    }

    /**
     * 오른쪽 정렬, 앞자리는 0으로 채운다.
     */
    private static void writeN(byte[] bytes, Field field, String digits) {
        if (digits.length() > field.length()) {
            throw new IllegalArgumentException("N 필드 길이를 넘는다: " + digits);
        }
        int start = field.offset() + field.length() - digits.length();
        Arrays.fill(bytes, field.offset(), start, (byte) '0');
        System.arraycopy(digits.getBytes(StandardCharsets.US_ASCII), 0, bytes, start, digits.length());
    }

    /**
     * EUC-KR로 왼쪽 정렬, 남는 자리는 SPACE로 채운다.
     */
    private static void writeAN(byte[] bytes, Field field, String value) {
        byte[] encoded = value.getBytes(CHARSET);
        if (encoded.length > field.length()) {
            throw new IllegalArgumentException("AN 필드 길이를 넘는다: " + value);
        }
        System.arraycopy(encoded, 0, bytes, field.offset(), encoded.length);
        Arrays.fill(bytes, field.offset() + encoded.length, field.offset() + field.length(), (byte) ' ');
    }

    private static String readAN(byte[] bytes, Field field) {
        return new String(bytes, field.offset(), field.length(), CHARSET).stripTrailing();
    }

    /**
     * 본문(300바이트) 안의 필드 위치.
     */
    private record Field(int offset, int length) {
    }
}
