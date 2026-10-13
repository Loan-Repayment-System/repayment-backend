package com.repayment.domain.baserate.service;

import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("기준금리 저장")
class BaseRateHistoryServiceTest {

    @Mock
    private BaseRateHistoryMapper baseRateHistoryMapper;

    @InjectMocks
    private BaseRateHistoryService baseRateHistoryService;

    @Nested
    @DisplayName("조회한 금리가 없을 때")
    class 조회한_금리_없음 {

        @Test
        @DisplayName("0건을 돌려주고 DB를 조회하거나 INSERT하지 않는다")
        void DB를_건드리지_않고_0을_돌려준다() {
            // given
            List<BaseRateResponse> rates = List.of();

            // when
            int savedCount = baseRateHistoryService.saveNewRates(rates);

            // then
            assertThat(savedCount).isZero();
            then(baseRateHistoryMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("조회한 공시일 중 일부가 이미 저장돼 있을 때")
    class 일부_공시일_저장됨 {

        // 프로그램 설계 1단계: 이미 있는 공시일은 건너뛰고 없는 공시일만 넣는다
        @Test
        @DisplayName("10/8·10/9·10/12 중 10/9가 있으면 10/8~10/12에서 있는 날을 찾아 10/8·10/12만 INSERT한다")
        void 없는_공시일만_INSERT한다() {
            // given
            List<BaseRateResponse> rates = List.of(
                    rate(LocalDate.of(2026, 10, 8), "2.84500"),
                    rate(LocalDate.of(2026, 10, 9), "2.85000"),
                    rate(LocalDate.of(2026, 10, 12), "2.85500"));
            given(baseRateHistoryMapper.findAnnouncedDates(
                    LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12)))
                    .willReturn(List.of(LocalDate.of(2026, 10, 9)));
            given(baseRateHistoryMapper.insertBaseRates(anyList())).willReturn(2);

            // when
            int savedCount = baseRateHistoryService.saveNewRates(rates);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<BaseRateResponse>> captor = ArgumentCaptor.forClass(List.class);
            then(baseRateHistoryMapper).should().insertBaseRates(captor.capture());
            assertThat(captor.getValue())
                    .extracting(BaseRateResponse::getAnnouncedDate)
                    .containsExactly(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12));
            assertThat(savedCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("조회한 공시일이 모두 이미 저장돼 있을 때")
    class 모든_공시일_저장됨 {

        // 배치 명세서 fetchBaseRateStep: 몇 번 실행해도 결과가 같다
        @Test
        @DisplayName("INSERT하지 않고 0건을 돌려준다")
        void INSERT하지_않고_0을_돌려준다() {
            // given
            List<BaseRateResponse> rates = List.of(
                    rate(LocalDate.of(2026, 10, 8), "2.84500"),
                    rate(LocalDate.of(2026, 10, 12), "2.85500"));
            given(baseRateHistoryMapper.findAnnouncedDates(any(), any()))
                    .willReturn(List.of(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12)));

            // when
            int savedCount = baseRateHistoryService.saveNewRates(rates);

            // then
            assertThat(savedCount).isZero();
            then(baseRateHistoryMapper).should(never()).insertBaseRates(anyList());
        }
    }

    private static BaseRateResponse rate(LocalDate announcedDate, String rate) {
        return BaseRateResponse.builder()
                .announcedDate(announcedDate)
                .rate(new BigDecimal(rate))
                .build();
    }
}
