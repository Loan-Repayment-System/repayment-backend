package com.repayment.domain.repayment.client;

import com.repayment.global.config.FirmbankingProperties;
import com.repayment.global.config.RepaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("펌뱅킹 집금 클라이언트")
class FirmbankingClientTest {

    private static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 12, 14, 30, 5);
    private static final int TELEGRAM_NO = 7;
    private static final String WITHDRAWAL_BANK_CODE = "999";
    private static final String WITHDRAWAL_ACCOUNT_NO = "200-000-000001";
    private static final BigDecimal AMOUNT = new BigDecimal("50000");

    @Mock
    private FirmbankingTelegramSender firmbankingTelegramSender;

    private FirmbankingClient client;

    @BeforeEach
    void setUp() {
        FirmbankingProperties firmbankingProperties = new FirmbankingProperties(
                "localhost", 9001, "CAPITAL00001", Duration.ofSeconds(3), Duration.ofSeconds(5),
                Duration.ofSeconds(1));
        RepaymentProperties repaymentProperties =
                new RepaymentProperties("999", "100-000-000001", "999", "100-000-000002");
        client = new FirmbankingClient(firmbankingTelegramSender, firmbankingProperties, repaymentProperties);
    }

    @Test
    @DisplayName("업체번호·2000/200·전문번호 000007·20261112 143005·금액 0000000050000·수납 계좌·적요를 전문에 쓴다")
    void 멱등_키와_집금_내용을_전문에_쓴다() {
        // given
        given(firmbankingTelegramSender.send(any())).willReturn(responseWithCode("0000"));

        // when
        client.requestCollection(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

        // then
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        then(firmbankingTelegramSender).should().send(captor.capture());
        byte[] request = captor.getValue();
        assertThat(request).hasSize(300);
        assertThat(read(request, 9, 12)).isEqualTo("CAPITAL00001");
        assertThat(read(request, 21, 3)).isEqualTo("999");
        assertThat(read(request, 24, 4)).isEqualTo("2000");
        assertThat(read(request, 28, 3)).isEqualTo("200");
        assertThat(read(request, 31, 1)).isEqualTo("1");
        assertThat(read(request, 32, 6)).isEqualTo("000007");
        assertThat(read(request, 38, 8)).isEqualTo("20261112");
        assertThat(read(request, 46, 6)).isEqualTo("143005");
        assertThat(read(request, 100, 15)).isEqualTo("200-000-000001 ");
        assertThat(read(request, 129, 13)).isEqualTo("0000000050000");
        assertThat(read(request, 156, 3)).isEqualTo("999");
        assertThat(read(request, 159, 15)).isEqualTo("100-000-000001 ");
        assertThat(read(request, 183, 14).stripTrailing()).isEqualTo("상환금");
    }

    @Test
    @DisplayName("응답코드가 0000이면 0000을 돌려준다")
    void 성공_응답코드를_돌려준다() {
        // given
        given(firmbankingTelegramSender.send(any())).willReturn(responseWithCode("0000"));

        // when
        String responseCode =
                client.requestCollection(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

        // then
        assertThat(responseCode).isEqualTo("0000");
    }

    @Test
    @DisplayName("응답코드가 잔액 부족 B003이면 예외 없이 B003을 돌려준다")
    void 거절_응답코드도_그대로_돌려준다() {
        // given
        given(firmbankingTelegramSender.send(any())).willReturn(responseWithCode("B003"));

        // when
        String responseCode =
                client.requestCollection(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

        // then
        assertThat(responseCode).isEqualTo("B003");
    }

    private static byte[] responseWithCode(String responseCode) {
        byte[] response = new byte[300];
        Arrays.fill(response, (byte) ' ');
        System.arraycopy(responseCode.getBytes(StandardCharsets.US_ASCII), 0, response, 52, 4);
        return response;
    }

    private static String read(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, CHARSET);
    }
}
