package com.repayment.domain.repayment.client.codec;

import java.nio.charset.Charset;

/**
 * 전문 바이트 → Java 값. AN은 EUC-KR이고 뒤쪽 SPACE를 뺀다.
 */
public final class FieldDecoder {

    private static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final byte SPACE = ' ';

    private FieldDecoder() {
    }

    /**
     * 뒤쪽 SPACE를 뺀 값을 돌려준다.
     */
    public static String readAN(byte[] bytes, TelegramField field) {
        int end = field.offset() + field.length();
        while (end > field.offset() && bytes[end - 1] == SPACE) {
            end--;
        }
        return new String(bytes, field.offset(), end - field.offset(), CHARSET);
    }
}
