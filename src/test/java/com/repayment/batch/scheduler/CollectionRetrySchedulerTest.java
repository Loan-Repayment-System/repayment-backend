package com.repayment.batch.scheduler;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.domain.repayment.service.CollectionRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("집금 요청 재요청 스케줄 실행")
class CollectionRetrySchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime CLOCK_TIME = LocalDateTime.of(2026, 10, 17, 10, 0, 0, 789_000_000);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0, 0);
    private static final CollectionRequestResponse REQUEST_A =
            CollectionRequestResponse.builder().collectionRequestId(1L).build();
    private static final CollectionRequestResponse REQUEST_B =
            CollectionRequestResponse.builder().collectionRequestId(2L).build();

    @Mock
    private CollectionRequestMapper collectionRequestMapper;

    @Mock
    private CollectionRetryService collectionRetryService;

    private CollectionRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(CLOCK_TIME.atZone(SEOUL).toInstant(), SEOUL);
        scheduler = new CollectionRetryScheduler(collectionRequestMapper, collectionRetryService, clock);
    }

    @Nested
    @DisplayName("재요청 대상이 있을 때")
    class 대상_있음 {

        @Test
        @DisplayName("10:00:00.789에 실행하면 10:00:00으로 대상 A, B를 조회하고 A → B 순서로 각각 10:00:00에 재요청한다")
        void 조회한_순서대로_재요청한다() {
            // given
            given(collectionRequestMapper.findRetryTargets(NOW)).willReturn(List.of(REQUEST_A, REQUEST_B));

            // when
            scheduler.run();

            // then
            InOrder inOrder = inOrder(collectionRetryService);
            then(collectionRetryService).should(inOrder).retry(REQUEST_A, NOW);
            then(collectionRetryService).should(inOrder).retry(REQUEST_B, NOW);
        }

        @Test
        @DisplayName("A의 재요청이 예외여도 밖으로 던지지 않고 B를 재요청한다")
        void 한_건이_실패해도_다음_건을_재요청한다() {
            // given
            given(collectionRequestMapper.findRetryTargets(NOW)).willReturn(List.of(REQUEST_A, REQUEST_B));
            willThrow(new IllegalStateException("DB 오류")).given(collectionRetryService).retry(REQUEST_A, NOW);

            // when
            Throwable thrown = catchThrowable(scheduler::run);

            // then
            assertThat(thrown).isNull();
            then(collectionRetryService).should().retry(REQUEST_B, NOW);
        }
    }

    @Nested
    @DisplayName("재요청 대상이 없을 때")
    class 대상_없음 {

        @Test
        @DisplayName("재요청하지 않는다")
        void 재요청하지_않는다() {
            // given
            given(collectionRequestMapper.findRetryTargets(NOW)).willReturn(List.of());

            // when
            scheduler.run();

            // then
            then(collectionRetryService).shouldHaveNoInteractions();
        }
    }
}
