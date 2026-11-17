package com.repayment.domain.repayment.metrics;

import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.global.code.CollectionRequestStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 집금 요청 건수를 상태별 게이지로 등록한다. 값은 지표를 읽을 때마다 DB에서 센다.
 */
@Component
@RequiredArgsConstructor
public class CollectionRequestMetrics implements MeterBinder {

    static final String NAME = "collection.request.count";

    private final CollectionRequestMapper collectionRequestMapper;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (CollectionRequestStatus status : CollectionRequestStatus.values()) {
            Gauge.builder(NAME, () -> collectionRequestMapper.countByStatus(status))
                    .tag("status", status.name())
                    .description("상태별 집금 요청 건수")
                    .register(registry);
        }
    }
}
