package com.repayment.domain.repayment.client;

import com.repayment.global.config.FirmbankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 펌뱅킹 전문 서버와 전문 하나를 주고받는다. 거래 종류와 관계없이 연결 하나에
 * 길이 헤더 0300 + 본문 300바이트를 보내고, 같은 형식의 응답에서 본문만 돌려준다.
 *
 * 연결 실패·응답 시간 초과·응답 없이 끊김·길이 헤더 오류는 상대가 처리했는지 알 수 없으므로
 * UncheckedIOException을 던진다.
 */
@Component
@RequiredArgsConstructor
public class FirmbankingTelegramSender {

    static final int TELEGRAM_LENGTH = 300;
    private static final byte[] LENGTH_HEADER = "0300".getBytes(StandardCharsets.US_ASCII);

    private final FirmbankingProperties firmbankingProperties;

    /**
     * 응답 시간은 read 한 번마다 적용한다. 펌뱅킹은 응답을 한 번에 쓰고 연결을 닫는다.
     */
    public byte[] send(byte[] request) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(firmbankingProperties.host(), firmbankingProperties.port()),
                    (int) firmbankingProperties.connectTimeout().toMillis());
            socket.setSoTimeout((int) firmbankingProperties.readTimeout().toMillis());

            OutputStream out = socket.getOutputStream();
            out.write(LENGTH_HEADER);
            out.write(request);
            out.flush();

            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] header = new byte[LENGTH_HEADER.length];
            in.readFully(header);
            if (!Arrays.equals(header, LENGTH_HEADER)) {
                throw new ProtocolException(
                        "응답 길이 헤더가 0300이 아니다: " + new String(header, StandardCharsets.US_ASCII));
            }
            byte[] response = new byte[TELEGRAM_LENGTH];
            in.readFully(response);
            return response;
        } catch (IOException e) {
            throw new UncheckedIOException("펌뱅킹 전문 송수신 실패", e);
        }
    }
}
