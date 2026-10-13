package com.repayment.domain.baserate.service;

import com.repayment.domain.baserate.client.BokApiClient;
import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("기준금리 수집")
class BaseRateServiceTest {

    @Mock
    private BokApiClient bokApiClient;

    @Mock
    private BaseRateHistoryService baseRateHistoryService;

    @InjectMocks
    private BaseRateService baseRateService;

    @Nested
    @DisplayName("한국은행 API 호출이 성공할 때")
    class 호출_성공 {

        // 프로그램 설계 1단계: 조회 기간은 기준일을 포함한 최근 15일
        @Test
        @DisplayName("기준일이 2026-10-13이면 2026-09-29부터 2026-10-13까지 15일을 조회한다")
        void 기준일을_포함한_15일을_조회한다() {
            // given
            LocalDate businessDate = LocalDate.of(2026, 10, 13);
            given(bokApiClient.fetchKoribor12m(any(), any())).willReturn(List.of());

            // when
            baseRateService.collectKoribor12m(businessDate);

            // then
            then(bokApiClient).should().fetchKoribor12m(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 13));
        }

        @Test
        @DisplayName("조회한 3건을 저장에 넘기고, 새로 넣은 2건을 돌려준다")
        void 조회한_금리를_저장에_넘기고_넣은_건수를_돌려준다() {
            // given
            LocalDate businessDate = LocalDate.of(2026, 10, 13);
            List<BaseRateResponse> rates = List.of(
                    rate(LocalDate.of(2026, 10, 8), "2.84500"),
                    rate(LocalDate.of(2026, 10, 12), "2.85000"),
                    rate(LocalDate.of(2026, 10, 13), "2.85500"));
            given(bokApiClient.fetchKoribor12m(any(), any())).willReturn(rates);
            given(baseRateHistoryService.saveNewRates(rates)).willReturn(2);

            // when
            int savedCount = baseRateService.collectKoribor12m(businessDate);

            // then
            assertThat(savedCount).isEqualTo(2);
            then(baseRateHistoryService).should().saveNewRates(rates);
        }
    }

    @Nested
    @DisplayName("한국은행 API 호출이 실패할 때")
    class 호출_실패 {

        @Test
        @DisplayName("클라이언트가 던진 예외가 그대로 나오고 저장은 하지 않는다")
        void 예외가_그대로_나오고_저장하지_않는다() {
            // given
            LocalDate businessDate = LocalDate.of(2026, 10, 13);
            ResourceAccessException exception = new ResourceAccessException("Read timed out");
            given(bokApiClient.fetchKoribor12m(any(), any())).willThrow(exception);

            // when
            Throwable thrown = catchThrowable(
                    () -> baseRateService.collectKoribor12m(businessDate));

            // then
            assertThat(thrown).isSameAs(exception);
            then(baseRateHistoryService).shouldHaveNoInteractions();
        }
    }

    private static BaseRateResponse rate(LocalDate announcedDate, String rate) {
        return BaseRateResponse.builder()
                .announcedDate(announcedDate)
                .rate(new BigDecimal(rate))
                .build();
    }
}
