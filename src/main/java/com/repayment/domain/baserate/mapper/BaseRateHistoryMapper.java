package com.repayment.domain.baserate.mapper;

import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BaseRateHistoryMapper {

    List<LocalDate> findAnnouncedDates(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    int insertBaseRates(List<BaseRateResponse> rates);
}
