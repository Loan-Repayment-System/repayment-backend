package com.repayment.domain.schedule.service;

import com.repayment.domain.baserate.exception.BaseRateNotFoundException;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.global.code.InterestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("적용 금리 결정")
class AppliedRateServiceTest {

    private static final BigDecimal CONTRACT_BASE_RATE = new BigDecimal("3.5");
    private static final BigDecimal CONTRACT_ADDITIONAL_RATE = new BigDecimal("1.5");
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 10, 25);
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 20);

    @Mock
    private BaseRateHistoryMapper baseRateHistoryMapper;

    @InjectMocks
    private AppliedRateService appliedRateService;

    @Nested
    @DisplayName("고정금리일 때")
    class 고정금리 {

        @Test
        @DisplayName("계약 기준금리 3.5%, 가산금리 1.5%를 그대로 쓰고 기준금리 이력은 조회하지 않는다")
        void 계약_금리를_그대로_쓴다() {
            // given
            InterestType interestType = InterestType.FIXED;

            // when
            AppliedRateResponse result = appliedRateService.resolve(
                    interestType, CONTRACT_BASE_RATE, CONTRACT_ADDITIONAL_RATE, SCHEDULED_DATE);

            // then
            assertThat(result.getBaseRate()).isEqualByComparingTo("3.5");
            assertThat(result.getAdditionalRate()).isEqualByComparingTo("1.5");
            then(baseRateHistoryMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("변동금리일 때")
    class 변동금리 {

        // 프로그램 설계 상환금 확정: 변동금리의 기준 날짜는 '예정일 − 5일'로 고정한다
        @Test
        @DisplayName("예정일 10/25면 10/20 이전 최근 공시 금리 2.845%를 기준금리로, 계약 가산금리 1.5%를 쓴다")
        void 예정일_5일_전_기준_공시_금리를_쓴다() {
            // given
            given(baseRateHistoryMapper.findLatestRateBefore(REFERENCE_DATE))
                    .willReturn(Optional.of(new BigDecimal("2.845")));

            // when
            AppliedRateResponse result = appliedRateService.resolve(
                    InterestType.VARIABLE, CONTRACT_BASE_RATE, CONTRACT_ADDITIONAL_RATE, SCHEDULED_DATE);

            // then
            assertThat(result.getBaseRate()).isEqualByComparingTo("2.845");
            assertThat(result.getAdditionalRate()).isEqualByComparingTo("1.5");
            then(baseRateHistoryMapper).should().findLatestRateBefore(REFERENCE_DATE);
        }

        @Test
        @DisplayName("10/20 이전 공시 금리가 없으면 기준 날짜 10/20을 담은 BaseRateNotFoundException을 던진다")
        void 공시_금리가_없으면_예외를_던진다() {
            // given
            given(baseRateHistoryMapper.findLatestRateBefore(REFERENCE_DATE))
                    .willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> appliedRateService.resolve(
                    InterestType.VARIABLE, CONTRACT_BASE_RATE, CONTRACT_ADDITIONAL_RATE, SCHEDULED_DATE));

            // then
            assertThat(thrown).isInstanceOf(BaseRateNotFoundException.class);
            assertThat(((BaseRateNotFoundException) thrown).getReferenceDate()).isEqualTo(REFERENCE_DATE);
        }
    }
}
