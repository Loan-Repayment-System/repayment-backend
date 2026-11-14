package com.repayment.domain.repayment.client;

import com.repayment.global.config.FirmbankingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("펌뱅킹 전문 송수신")
class FirmbankingTelegramSenderTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);

    private ServerSocket serverSocket;
    private ExecutorService serverThread;
    private FirmbankingTelegramSender sender;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverThread = Executors.newSingleThreadExecutor();
        sender = senderFor(serverSocket.getLocalPort());
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
        @DisplayName("길이 헤더 0300과 요청 300바이트를 보내고, 길이 헤더를 뺀 응답 300바이트를 돌려준다")
        void 전문_하나를_주고받는다() throws Exception {
            // given
            byte[] request = filled((byte) 'Q');
            byte[] response = filled((byte) 'R');
            Future<byte[]> received = serverThread.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    byte[] all = new DataInputStream(socket.getInputStream()).readNBytes(304);
                    writeResponse(socket, "0300", response);
                    return all;
                }
            });

            // when
            byte[] result = sender.send(request);

            // then
            byte[] all = received.get(1, TimeUnit.SECONDS);
            assertThat(new String(all, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("0300");
            assertThat(Arrays.copyOfRange(all, 4, 304)).isEqualTo(request);
            assertThat(result).isEqualTo(response);
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
            Throwable thrown = catchThrowable(() -> sender.send(filled((byte) 'Q')));

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
            Throwable thrown = catchThrowable(() -> sender.send(filled((byte) 'Q')));

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
            FirmbankingTelegramSender closedPortSender = senderFor(closedPort);

            // when
            Throwable thrown = catchThrowable(() -> closedPortSender.send(filled((byte) 'Q')));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("응답 길이 헤더가 0299면 UncheckedIOException을 던진다")
        void 응답_형식_오류() {
            // given
            serverThread.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket);
                    writeResponse(socket, "0299", filled((byte) 'R'));
                }
                return null;
            });

            // when
            Throwable thrown = catchThrowable(() -> sender.send(filled((byte) 'Q')));

            // then
            assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        }
    }

    private static FirmbankingTelegramSender senderFor(int port) {
        return new FirmbankingTelegramSender(new FirmbankingProperties(
                InetAddress.getLoopbackAddress().getHostAddress(), port, "CAPITAL00001",
                Duration.ofSeconds(1), READ_TIMEOUT, Duration.ofSeconds(1)));
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[300];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static void readRequest(Socket socket) throws IOException {
        new DataInputStream(socket.getInputStream()).readFully(new byte[304]);
    }

    private static void writeResponse(Socket socket, String lengthHeader, byte[] body) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(lengthHeader.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
