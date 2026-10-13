package com.repayment.domain.baserate.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("기준금리 이력 매퍼")
class BaseRateHistoryMapperIntegrationTest {

    @Autowired
    private BaseRateHistoryMapper baseRateHistoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("저장된 기준금리가 없을 때")
    class 저장된_기준금리_없음 {

        @Test
        @DisplayName("2건을 INSERT하면 2를 돌려주고 공시일·금리 2.84500이 그대로 저장된다")
        void 여러_건을_한_번에_INSERT한다() {
            // given
            List<BaseRateResponse> rates = List.of(
                    rate(LocalDate.of(2026, 10, 8), "2.84500"),
                    rate(LocalDate.of(2026, 10, 12), "2.85000"));

            // when
            int insertedCount = baseRateHistoryMapper.insertBaseRates(rates);

            // then
            assertThat(insertedCount).isEqualTo(2);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT announced_date, rate FROM base_rate_history ORDER BY announced_date");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).get("announced_date").toString()).isEqualTo("2026-10-08");
            assertThat((BigDecimal) rows.get(0).get("rate")).isEqualByComparingTo("2.84500");
            assertThat(rows.get(1).get("announced_date").toString()).isEqualTo("2026-10-12");
            assertThat((BigDecimal) rows.get(1).get("rate")).isEqualByComparingTo("2.85000");
        }
    }

    @Nested
    @DisplayName("10/1·10/5·10/10 기준금리가 저장돼 있을 때")
    class 기준금리_3건_저장됨 {

        @Test
        @DisplayName("10/2~10/10으로 조회하면 경계일을 포함해 10/5·10/10만 나온다")
        void 기간_안의_공시일만_조회한다() {
            // given
            baseRateHistoryMapper.insertBaseRates(List.of(
                    rate(LocalDate.of(2026, 10, 1), "2.84000"),
                    rate(LocalDate.of(2026, 10, 5), "2.84500"),
                    rate(LocalDate.of(2026, 10, 10), "2.85000")));

            // when
            List<LocalDate> announcedDates = baseRateHistoryMapper.findAnnouncedDates(
                    LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 10));

            // then
            assertThat(announcedDates)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10));
        }
    }

    @Nested
    @DisplayName("10/1 기준금리가 저장돼 있을 때")
    class 기준금리_10월1일_저장됨 {

        @Test
        @DisplayName("같은 공시일을 다시 INSERT하면 유니크 키에 걸려 DuplicateKeyException이 난다")
        void 같은_공시일을_다시_넣으면_중복_키_예외가_난다() {
            // given
            baseRateHistoryMapper.insertBaseRates(
                    List.of(rate(LocalDate.of(2026, 10, 1), "2.84000")));

            // when
            Throwable thrown = catchThrowable(() -> baseRateHistoryMapper.insertBaseRates(
                    List.of(rate(LocalDate.of(2026, 10, 1), "2.84500"))));

            // then
            assertThat(thrown).isInstanceOf(DuplicateKeyException.class);
        }
    }

    private static BaseRateResponse rate(LocalDate announcedDate, String rate) {
        return BaseRateResponse.builder()
                .announcedDate(announcedDate)
                .rate(new BigDecimal(rate))
                .build();
    }
}
