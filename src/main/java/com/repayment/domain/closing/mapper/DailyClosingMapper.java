package com.repayment.domain.closing.mapper;

import com.repayment.global.code.DailyClosingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface DailyClosingMapper {

    Optional<DailyClosingStatus> findStatusByBusinessDate(LocalDate businessDate);

    boolean existsBeforeBusinessDate(LocalDate businessDate);

    int insertInProgress(@Param("businessDate") LocalDate businessDate,
                         @Param("startedAt") LocalDateTime startedAt);

    int updateFailedToInProgress(@Param("businessDate") LocalDate businessDate,
                                 @Param("startedAt") LocalDateTime startedAt);

    int updateInProgressToFinished(@Param("businessDate") LocalDate businessDate,
                                   @Param("status") DailyClosingStatus status,
                                   @Param("finishedAt") LocalDateTime finishedAt);
}
