package com.repayment.batch.scheduler;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.domain.repayment.service.CollectionRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * 다음 시도 시각이 지난 집금 요청을 이른 것부터 1건씩 다시 처리한다.
 * 이전 실행이 끝난 뒤 1분을 세므로 실행이 길어져도 겹치지 않는다.
 * 한 건이 실패해도 로그만 남기고 다음 건을 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionRetryScheduler {

    private final CollectionRequestMapper collectionRequestMapper;
    private final CollectionRetryService collectionRetryService;
    private final Clock clock;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void run() {
        for (CollectionRequestResponse request : collectionRequestMapper.findRetryTargets(now())) {
            retry(request);
        }
    }

    // 앞 건이 응답을 기다린 만큼 늦어지므로, 다음 시도 시각은 그 건을 처리하는 시각으로 계산한다.
    private void retry(CollectionRequestResponse request) {
        try {
            collectionRetryService.retry(request, now());
        } catch (RuntimeException e) {
            log.error("미확정 집금 재처리 실패 collectionRequestId={}", request.getCollectionRequestId(), e);
        }
    }

    // DATETIME은 초 미만을 반올림해 저장하므로 먼저 버린다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
