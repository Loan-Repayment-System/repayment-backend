package com.repayment.domain.repayment.client.field;

import com.repayment.domain.repayment.client.codec.TelegramField;

/**
 * 집금이체 2000/200 개별부 필드 위치. 펌뱅킹 전문 명세 4.2절과 같다.
 */
public final class CollectionField {

    public static final TelegramField WITHDRAWAL_ACCOUNT_NO = new TelegramField(100, 15);
    public static final TelegramField AMOUNT = new TelegramField(129, 13);
    public static final TelegramField BALANCE_SIGN = new TelegramField(142, 1);
    public static final TelegramField BALANCE_AFTER = new TelegramField(143, 13);
    public static final TelegramField DEPOSIT_BANK_CODE = new TelegramField(156, 3);
    public static final TelegramField DEPOSIT_ACCOUNT_NO = new TelegramField(159, 15);
    public static final TelegramField FEE = new TelegramField(174, 9);
    public static final TelegramField MEMO = new TelegramField(183, 14);

    private CollectionField() {
    }
}
