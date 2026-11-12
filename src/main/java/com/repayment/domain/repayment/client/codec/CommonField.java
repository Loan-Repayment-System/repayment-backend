package com.repayment.domain.repayment.client.codec;

/**
 * 공통부 필드 위치. 펌뱅킹 전문 명세 3절과 같다.
 */
public final class CommonField {

    public static final TelegramField COMPANY_NO = new TelegramField(9, 12);
    public static final TelegramField BANK_CODE = new TelegramField(21, 3);
    public static final TelegramField TELEGRAM_TYPE_CODE = new TelegramField(24, 4);
    public static final TelegramField BUSINESS_TYPE_CODE = new TelegramField(28, 3);
    public static final TelegramField SEND_COUNT = new TelegramField(31, 1);
    public static final TelegramField TELEGRAM_NO = new TelegramField(32, 6);
    public static final TelegramField SEND_DATE = new TelegramField(38, 8);
    public static final TelegramField SEND_TIME = new TelegramField(46, 6);
    public static final TelegramField RESPONSE_CODE = new TelegramField(52, 4);

    private CommonField() {
    }
}
