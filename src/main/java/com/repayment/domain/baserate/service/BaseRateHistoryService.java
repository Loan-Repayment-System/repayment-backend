package com.repayment.domain.baserate.service;

import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 조회한 기준금리를 base_rate_history에 저장한다.
 * API 호출(BaseRateService)과 빈을 나눠, 저장만 한 트랜잭션으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class BaseRateHistoryService {

    private final BaseRateHistoryMapper baseRateHistoryMapper;

    /**
     * 없는 공시일만 INSERT하고 넣은 건수를 돌려준다. 이미 있는 공시일은 금리가 달라도 그대로 둔다.
     */
    @Transactional
    public int saveNewRates(List<BaseRateResponse> rates) {
        if (rates.isEmpty()) {
            return 0;
        }
        LocalDate startDate = rates.stream().map(BaseRateResponse::getAnnouncedDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate endDate = rates.stream().map(BaseRateResponse::getAnnouncedDate)
                .max(Comparator.naturalOrder()).orElseThrow();
        Set<LocalDate> savedDates = new HashSet<>(
                baseRateHistoryMapper.findAnnouncedDates(startDate, endDate));

        List<BaseRateResponse> newRates = rates.stream()
                .filter(rate -> !savedDates.contains(rate.getAnnouncedDate()))
                .toList();
        if (newRates.isEmpty()) {
            return 0;
        }
        return baseRateHistoryMapper.insertBaseRates(newRates);
    }
}
