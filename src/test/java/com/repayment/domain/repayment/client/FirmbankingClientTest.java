package com.repayment.domain.repayment.client;

import com.repayment.global.config.FirmbankingProperties;
import com.repayment.global.config.RepaymentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("펌뱅킹 집금 클라이언트")
class FirmbankingClientTest {

    private static final Charset CHARSET = Charset.forName("EUC-KR");
    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);
    private static final RepaymentProperties REPAYMENT_PROPERTIES =
            new RepaymentProperties("999", "100-000-000001", "999", "100-000-000002");

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 12, 14, 30, 5);
    private static final int TELEGRAM_NO = 7;
    private static final String WITHDRAWAL_BANK_CODE = "999";
    private static final String WITHDRAWAL_ACCOUNT_NO = "200-000-000001";
    private static final BigDecimal AMOUNT = new BigDecimal("50000");

    private ServerSocket serverSocket;
    private ExecutorService serverThread;
    private FirmbankingClient client;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverThread = Executors.newSingleThreadExecutor();
        client = clientFor(serverSocket.getLocalPort());
    }

    @AfterEach
    void tearDown() throws IOException {
        serverThread.shutdownNow();
        serverSocket.close();
    }

    @Nested
    @DisplayName("펌뱅킹이 응답할 때")
    class 펌뱅킹이_응답 {

        @Test
        @DisplayName("업체번호·2000/200·전문번호 000007·20261112 143005·금액 0000000050000·수납 계좌·적요를 전문에 쓴다")
        void 멱등_키와_집금_내용을_전문에_쓴다() throws Exception {
            // given
            Future<byte[]> received = respondWith("0000");

            // when
            client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

            // then
            byte[] request = received.get(1, TimeUnit.SECONDS);
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
            respondWith("0000");

            // when
            String responseCode =
                    client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

            // then
            assertThat(responseCode).isEqualTo("0000");
        }

        @Test
        @DisplayName("응답코드가 잔액 부족 B003이면 예외 없이 B003을 돌려준다")
        void 거절_응답코드도_그대로_돌려준다() {
            // given
            respondWith("B003");

            // when
            String responseCode =
                    client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT);

            // then
            assertThat(responseCode).isEqualTo("B003");
        }
    }

    @Nested
    @DisplayName("응답을 받지 못할 때")
    class 응답을_받지_못함 {

        @Test
        @DisplayName("응답 시간 200ms가 지나도 응답이 없으면 UncheckedIOException을 던진다")
        void 응답_시간_초과() {
            // given
            serverThread.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket);
                    Thread.sleep(READ_TIMEOUT.multipliedBy(5).toMillis());
                }
                return null;
            });

            // when
            Throwable thrown = catchThrowable(() ->
                    client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("펌뱅킹이 요청을 받고 응답 없이 연결을 닫으면 UncheckedIOException을 던진다")
        void 응답_없이_끊김() {
            // given
            serverThread.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket);
                }
                return null;
            });

            // when
            Throwable thrown = catchThrowable(() ->
                    client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("열려 있지 않은 포트로 연결하면 UncheckedIOException을 던진다")
        void 연결_실패() throws IOException {
            // given
            int closedPort;
            try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                closedPort = closed.getLocalPort();
            }
            FirmbankingClient closedPortClient = clientFor(closedPort);

            // when
            Throwable thrown = catchThrowable(() -> closedPortClient.collect(
                    SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("응답 길이 헤더가 0299면 UncheckedIOException을 던진다")
        void 응답_형식_오류() {
            // given
            serverThread.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    byte[] request = readRequest(socket);
                    writeResponse(socket, "0299", withResponseCode(request, "0000"));
                }
                return null;
            });

            // when
            Throwable thrown = catchThrowable(() ->
                    client.collect(SENT_AT, TELEGRAM_NO, WITHDRAWAL_BANK_CODE, WITHDRAWAL_ACCOUNT_NO, AMOUNT));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }
    }

    private FirmbankingClient clientFor(int port) {
        FirmbankingProperties properties = new FirmbankingProperties(
                InetAddress.getLoopbackAddress().getHostAddress(), port, "CAPITAL00001",
                Duration.ofSeconds(1), READ_TIMEOUT);
        return new FirmbankingClient(properties, REPAYMENT_PROPERTIES);
    }

    /**
     * 요청 하나를 받아 응답코드만 바꿔 돌려보내는 가짜 펌뱅킹. 받은 요청 본문을 돌려준다.
     */
    private Future<byte[]> respondWith(String responseCode) {
        return serverThread.submit(() -> {
            try (Socket socket = serverSocket.accept()) {
                byte[] request = readRequest(socket);
                writeResponse(socket, "0300", withResponseCode(request, responseCode));
                return request;
            }
        });
    }

    private static byte[] readRequest(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] header = new byte[4];
        in.readFully(header);
        byte[] body = new byte[300];
        in.readFully(body);
        return body;
    }

    private static void writeResponse(Socket socket, String lengthHeader, byte[] body) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(lengthHeader.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static byte[] withResponseCode(byte[] request, String responseCode) {
        byte[] response = request.clone();
        System.arraycopy(responseCode.getBytes(StandardCharsets.US_ASCII), 0, response, 52, 4);
        return response;
    }

    private static String read(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, CHARSET);
    }
}
