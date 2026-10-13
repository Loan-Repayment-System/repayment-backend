package com.repayment.domain.baserate.mapper;

import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BaseRateHistoryMapper {

    List<LocalDate> findAnnouncedDates(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    int insertBaseRates(List<BaseRateResponse> rates);

    Optional<BigDecimal> findLatestRateBefore(LocalDate date);
}
