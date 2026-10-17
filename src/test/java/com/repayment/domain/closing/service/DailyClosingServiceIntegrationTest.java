package com.repayment.domain.closing.service;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.closing.exception.DailyClosingAlreadyCompletedException;
import com.repayment.domain.closing.exception.DailyClosingAlreadyRunningException;
import com.repayment.domain.closing.exception.DailyClosingNotRunningException;
import com.repayment.domain.closing.exception.PreviousDailyClosingNotCompletedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 분기가 기준일 행의 유무와 UPDATE로 바뀐 행 수에 달려 있어 매퍼 SQL까지 함께 검증한다.
@SpringBootTest
@Import({TestcontainersConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@Transactional
@DisplayName("일마감 실행 기록")
class DailyClosingServiceIntegrationTest {

    // daily_closing_status
    private static final int IN_PROGRESS = 1;
    private static final int COMPLETED = 2;
    private static final int FAILED = 3;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDateTime NOW = FixedClockConfig.NOW;
    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 10, 16, 23, 0);

    @Autowired
    private DailyClosingService dailyClosingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("일마감을 시작할 때 전날을 확인하면")
    class 시작_전날_확인 {

        @Test
        @DisplayName("앞선 행이 하나도 없으면 첫 실행으로 보고 10/17 행을 진행중으로 만든다")
        void 첫_실행이면_전날_검사_없이_시작한다() {
            // when
            dailyClosingService.start(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(IN_PROGRESS);
            assertThat(toLocalDateTime(row.get("started_at"))).isEqualTo(NOW);
            assertThat(row.get("finished_at")).isNull();
        }

        @Test
        @DisplayName("전날 10/16이 실패면 거절하고 10/17 행을 만들지 않는다")
        void 전날이_실패면_거절한다() {
            // given
            insertRow(YESTERDAY, FAILED, EARLIER, EARLIER);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.start(TODAY))
                    .isInstanceOf(PreviousDailyClosingNotCompletedException.class);
            assertThat(countRows(TODAY)).isZero();
        }

        @Test
        @DisplayName("전날 10/16이 진행중이면 거절한다")
        void 전날이_진행중이면_거절한다() {
            // given
            insertRow(YESTERDAY, IN_PROGRESS, EARLIER, null);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.start(TODAY))
                    .isInstanceOf(PreviousDailyClosingNotCompletedException.class);
        }

        // 빠진 날은 그날부터 순서대로 실행한다
        @Test
        @DisplayName("이틀 전 10/15는 완료인데 전날 10/16 행이 없으면 거절한다")
        void 전날_행이_빠졌으면_거절한다() {
            // given
            insertRow(TODAY.minusDays(2), COMPLETED, EARLIER, EARLIER);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.start(TODAY))
                    .isInstanceOf(PreviousDailyClosingNotCompletedException.class);
        }
    }

    @Nested
    @DisplayName("전날 10/16이 완료이고 10/17 일마감을 시작할 때")
    class 시작_오늘_행 {

        @Test
        @DisplayName("10/17 행이 없으면 진행중으로 만든다")
        void 오늘_행이_없으면_진행중으로_만든다() {
            // given
            insertRow(YESTERDAY, COMPLETED, EARLIER, EARLIER);

            // when
            dailyClosingService.start(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(IN_PROGRESS);
            assertThat(toLocalDateTime(row.get("started_at"))).isEqualTo(NOW);
            assertThat(row.get("finished_at")).isNull();
        }

        @Test
        @DisplayName("10/17 행이 실패면 진행중으로 바꾸고 시작 시각을 새로 쓰고 종료 시각을 비운다")
        void 오늘_행이_실패면_다시_시작한다() {
            // given
            insertRow(YESTERDAY, COMPLETED, EARLIER, EARLIER);
            insertRow(TODAY, FAILED, EARLIER, EARLIER);

            // when
            dailyClosingService.start(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(IN_PROGRESS);
            assertThat(toLocalDateTime(row.get("started_at"))).isEqualTo(NOW);
            assertThat(row.get("finished_at")).isNull();
        }

        // 처리 실패 기록이 없어 완료된 날은 모든 대출을 처리한 날이다
        @Test
        @DisplayName("10/17 행이 완료면 거절하고 행을 그대로 둔다")
        void 오늘_행이_완료면_거절한다() {
            // given
            insertRow(YESTERDAY, COMPLETED, EARLIER, EARLIER);
            insertRow(TODAY, COMPLETED, EARLIER, EARLIER);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.start(TODAY))
                    .isInstanceOf(DailyClosingAlreadyCompletedException.class);
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(COMPLETED);
            assertThat(toLocalDateTime(row.get("started_at"))).isEqualTo(EARLIER);
        }

        @Test
        @DisplayName("10/17 행이 진행중이면 거절하고 행을 그대로 둔다")
        void 오늘_행이_진행중이면_거절한다() {
            // given
            insertRow(YESTERDAY, COMPLETED, EARLIER, EARLIER);
            insertRow(TODAY, IN_PROGRESS, EARLIER, null);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.start(TODAY))
                    .isInstanceOf(DailyClosingAlreadyRunningException.class);
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(IN_PROGRESS);
            assertThat(toLocalDateTime(row.get("started_at"))).isEqualTo(EARLIER);
        }
    }

    @Nested
    @DisplayName("일마감을 완료할 때")
    class 완료 {

        @Test
        @DisplayName("10/17 행이 진행중이면 완료로 바꾸고 종료 시각을 채운다")
        void 진행중이면_완료로_바꾼다() {
            // given
            insertRow(TODAY, IN_PROGRESS, EARLIER, null);

            // when
            dailyClosingService.complete(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(COMPLETED);
            assertThat(toLocalDateTime(row.get("finished_at"))).isEqualTo(NOW);
        }

        @Test
        @DisplayName("10/17 행을 담당자가 실패로 바꿨으면 거절하고 실패로 둔다")
        void 실패면_거절한다() {
            // given
            insertRow(TODAY, FAILED, EARLIER, EARLIER);

            // when, then
            assertThatThrownBy(() -> dailyClosingService.complete(TODAY))
                    .isInstanceOf(DailyClosingNotRunningException.class);
            assertThat(findRow(TODAY).get("daily_closing_status_id")).isEqualTo(FAILED);
        }

        @Test
        @DisplayName("10/17 행이 없으면 거절한다")
        void 행이_없으면_거절한다() {
            // when, then
            assertThatThrownBy(() -> dailyClosingService.complete(TODAY))
                    .isInstanceOf(DailyClosingNotRunningException.class);
        }
    }

    @Nested
    @DisplayName("일마감을 실패로 바꿀 때")
    class 실패 {

        @Test
        @DisplayName("10/17 행이 진행중이면 실패로 바꾸고 종료 시각을 채운다")
        void 진행중이면_실패로_바꾼다() {
            // given
            insertRow(TODAY, IN_PROGRESS, EARLIER, null);

            // when
            dailyClosingService.fail(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(FAILED);
            assertThat(toLocalDateTime(row.get("finished_at"))).isEqualTo(NOW);
        }

        // Job 실패 뒤 정리하는 자리라 예외로 원래 실패 원인을 가리지 않는다
        @Test
        @DisplayName("10/17 행이 완료면 예외 없이 완료와 종료 시각을 그대로 둔다")
        void 완료면_아무것도_바꾸지_않는다() {
            // given
            insertRow(TODAY, COMPLETED, EARLIER, EARLIER);

            // when
            dailyClosingService.fail(TODAY);

            // then
            Map<String, Object> row = findRow(TODAY);
            assertThat(row.get("daily_closing_status_id")).isEqualTo(COMPLETED);
            assertThat(toLocalDateTime(row.get("finished_at"))).isEqualTo(EARLIER);
        }
    }

    private void insertRow(LocalDate businessDate, int statusId, LocalDateTime startedAt, LocalDateTime finishedAt) {
        jdbcTemplate.update("INSERT INTO daily_closing (business_date, daily_closing_status_id, started_at, finished_at) "
                + "VALUES (?, ?, ?, ?)", businessDate, statusId, startedAt, finishedAt);
    }

    private Map<String, Object> findRow(LocalDate businessDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT daily_closing_status_id, started_at, finished_at FROM daily_closing WHERE business_date = ?",
                businessDate);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private int countRows(LocalDate businessDate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_closing WHERE business_date = ?", Integer.class, businessDate);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }
}
