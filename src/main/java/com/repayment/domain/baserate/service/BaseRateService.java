package com.repayment.domain.baserate.service;

import com.repayment.domain.baserate.client.BokApiClient;
import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 기준금리를 한국은행 API로 조회해 저장한다.
 * API를 기다리는 동안 DB 연결을 잡지 않도록 이 클래스에는 트랜잭션을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BaseRateService {

    // 기준일 포함 15일. 연휴가 길거나 앞서 며칠 실패해도 빠진 공시일을 따라잡는다.
    private static final int FETCH_DAYS = 15;

    private final BokApiClient bokApiClient;
    private final BaseRateHistoryService baseRateHistoryService;

    /**
     * 기준일 포함 최근 15일의 KORIBOR 12개월 금리를 조회해 없는 공시일만 저장하고, 새로 넣은 건수를 돌려준다.
     */
    public int collectKoribor12m(LocalDate businessDate) {
        LocalDate startDate = businessDate.minusDays(FETCH_DAYS - 1);
        List<BaseRateResponse> rates = bokApiClient.fetchKoribor12m(startDate, businessDate);
        return baseRateHistoryService.saveNewRates(rates);
    }
}
