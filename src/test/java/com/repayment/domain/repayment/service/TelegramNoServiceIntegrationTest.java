package com.repayment.domain.repayment.service;

import com.repayment.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채번은 부르는 쪽과 따로 커밋하므로 @Transactional로 롤백할 수 없다. 테스트마다 행을 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("펌뱅킹 전문번호 채번")
class TelegramNoServiceIntegrationTest {

    private static final LocalDate SEND_DATE = LocalDate.of(2026, 11, 12);

    @Autowired
    private TelegramNoService telegramNoService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM telegram_no_sequence");
    }

    @Nested
    @DisplayName("그날 채번 기록이 없을 때")
    class 그날_채번_기록_없음 {

        @Test
        @DisplayName("채번하면 1이다")
        void 날마다_1부터_매긴다() {
            // when
            int telegramNo = telegramNoService.next(SEND_DATE);

            // then
            assertThat(telegramNo).isEqualTo(1);
        }

        @Test
        @DisplayName("10개 스레드가 동시에 채번하면 1~10을 하나씩 겹치지 않게 받는다")
        void 동시에_채번해도_겹치지_않는다() throws Exception {
            // given
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return telegramNoService.next(SEND_DATE);
                }));
            }

            // when
            start.countDown();

            // then
            List<Integer> telegramNos = new ArrayList<>();
            for (Future<Integer> future : futures) {
                telegramNos.add(future.get());
            }
            executor.shutdown();
            assertThat(telegramNos).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        }
    }

    @Nested
    @DisplayName("그날 채번 기록이 있을 때")
    class 그날_채번_기록_있음 {

        @Test
        @DisplayName("1번까지 썼으면 다음 채번은 2다")
        void 이어서_매긴다() {
            // given
            telegramNoService.next(SEND_DATE);

            // when
            int telegramNo = telegramNoService.next(SEND_DATE);

            // then
            assertThat(telegramNo).isEqualTo(2);
        }

        @Test
        @DisplayName("11/12에 3번까지 썼어도 11/13 채번은 1이다")
        void 날짜마다_따로_매긴다() {
            // given
            telegramNoService.next(SEND_DATE);
            telegramNoService.next(SEND_DATE);
            telegramNoService.next(SEND_DATE);

            // when
            int telegramNo = telegramNoService.next(SEND_DATE.plusDays(1));

            // then
            assertThat(telegramNo).isEqualTo(1);
        }

        @Test
        @DisplayName("1번을 받은 부르는 쪽 트랜잭션이 롤백돼도 다음 채번은 2다")
        void 롤백돼도_보낸_번호를_다시_내주지_않는다() {
            // given
            transactionTemplate.executeWithoutResult(status -> {
                telegramNoService.next(SEND_DATE);
                status.setRollbackOnly();
            });

            // when
            int telegramNo = telegramNoService.next(SEND_DATE);

            // then
            assertThat(telegramNo).isEqualTo(2);
        }
    }
}
