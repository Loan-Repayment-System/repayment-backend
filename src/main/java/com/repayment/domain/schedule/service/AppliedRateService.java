package com.repayment.domain.schedule.service;

import com.repayment.domain.baserate.exception.BaseRateNotFoundException;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.global.code.InterestType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 회차 확정에 쓸 기준금리와 가산금리를 정한다.
 */
@Service
@RequiredArgsConstructor
public class AppliedRateService {

    private static final int REFERENCE_DAYS_BEFORE_SCHEDULED_DATE = 5;

    private final BaseRateHistoryMapper baseRateHistoryMapper;

    /**
     * 고정금리는 계약 기준금리를, 변동금리는 '예정일 − 5일'보다 앞서 공시된 가장 최근 금리를 기준금리로 쓴다.
     * 가산금리는 둘 다 계약 가산금리다.
     */
    public AppliedRateResponse resolve(InterestType interestType, BigDecimal contractBaseRate,
                                       BigDecimal contractAdditionalRate, LocalDate scheduledDate) {
        BigDecimal baseRate = switch (interestType) {
            case FIXED -> contractBaseRate;
            case VARIABLE -> findVariableBaseRate(scheduledDate);
        };
        return new AppliedRateResponse(baseRate, contractAdditionalRate);
    }

    private BigDecimal findVariableBaseRate(LocalDate scheduledDate) {
        LocalDate referenceDate = scheduledDate.minusDays(REFERENCE_DAYS_BEFORE_SCHEDULED_DATE);
        return baseRateHistoryMapper.findLatestRateBefore(referenceDate)
                .orElseThrow(() -> new BaseRateNotFoundException(referenceDate));
    }
}
