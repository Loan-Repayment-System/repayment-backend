package com.repayment.domain.repayment.metrics;

import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.global.code.CollectionRequestStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("집금 요청 건수 지표")
class CollectionRequestMetricsTest {

    @Mock
    private CollectionRequestMapper collectionRequestMapper;

    @InjectMocks
    private CollectionRequestMetrics collectionRequestMetrics;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("요청 건수가 37건이면 요청 상태 게이지 값은 37이다")
    void 게이지가_매퍼_값을_보여준다() {
        // given
        given(collectionRequestMapper.countByStatus(CollectionRequestStatus.REQUESTED)).willReturn(37L);
        collectionRequestMetrics.bindTo(registry);

        // when
        double value = registry.get(CollectionRequestMetrics.NAME).tag("status", "REQUESTED").gauge().value();

        // then
        assertThat(value).isEqualTo(37);
    }

    @Test
    @DisplayName("게이지를 등록하면 요청 · 집금 완료 · 상환 완료 · 거절 4개 상태 게이지가 모두 있다")
    void 상태마다_게이지를_등록한다() {
        // when
        collectionRequestMetrics.bindTo(registry);

        // then
        assertThat(registry.get(CollectionRequestMetrics.NAME).gauges())
                .extracting(gauge -> gauge.getId().getTag("status"))
                .containsExactlyInAnyOrder("REQUESTED", "COLLECTED", "COMPLETED", "FAILED");
    }
}
