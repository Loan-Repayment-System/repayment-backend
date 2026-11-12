package com.repayment.domain.repayment.client.codec;

/**
 * 전문 안의 필드 위치. offset은 본문(300바이트) 기준, length는 바이트 수다.
 */
public record TelegramField(int offset, int length) {

    /** 본문 = 공통부 100 + 개별부 200 */
    public static final int TELEGRAM_LENGTH = 300;
}
