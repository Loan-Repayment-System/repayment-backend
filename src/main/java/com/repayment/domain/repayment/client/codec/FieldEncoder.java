package com.repayment.domain.repayment.client.codec;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Java 값 → 전문 바이트. N은 오른쪽 정렬·앞 0, AN은 EUC-KR로 왼쪽 정렬·뒤 SPACE.
 * 쓸 값이 틀리면 코드 버그라 IllegalArgumentException이다.
 */
public final class FieldEncoder {

    private static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final byte SPACE = ' ';
    private static final byte ZERO = '0';

    private FieldEncoder() {
    }

    public static void writeN(byte[] bytes, TelegramField field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        writeDigits(bytes, field, Long.toString(value));
    }

    public static void writeAmount(byte[] bytes, TelegramField field, BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        try {
            writeDigits(bytes, field, value.toBigIntegerExact().toString());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("금액은 원 단위다: " + value);
        }
    }

    /**
     * 왼쪽부터 채우고 남는 자리는 SPACE. 길이를 넘으면 글자 경계에서 자른다.
     */
    public static void writeAN(byte[] bytes, TelegramField field, String value) {
        CharsetEncoder encoder = CHARSET.newEncoder();
        int position = field.offset();
        int end = field.offset() + field.length();
        for (int i = 0; i < value.length(); ) {
            int codePointLength = Character.charCount(value.codePointAt(i));
            String character = value.substring(i, i + codePointLength);
            if (!encoder.canEncode(character)) {
                throw new IllegalArgumentException("EUC-KR로 쓸 수 없는 문자다: " + character);
            }
            byte[] encoded = character.getBytes(CHARSET);
            if (position + encoded.length > end) {
                break;
            }
            System.arraycopy(encoded, 0, bytes, position, encoded.length);
            position += encoded.length;
            i += codePointLength;
        }
        Arrays.fill(bytes, position, end, SPACE);
    }

    private static void writeDigits(byte[] bytes, TelegramField field, String digits) {
        if (digits.length() > field.length()) {
            throw new IllegalArgumentException("N 필드 길이를 넘는다: " + digits + " " + describe(field));
        }
        int start = field.offset() + field.length() - digits.length();
        Arrays.fill(bytes, field.offset(), start, ZERO);
        System.arraycopy(digits.getBytes(StandardCharsets.US_ASCII), 0, bytes, start, digits.length());
    }

    private static String describe(TelegramField field) {
        return "(offset=" + field.offset() + ", length=" + field.length() + ")";
    }
}
