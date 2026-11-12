package com.repayment.domain.repayment.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

@Mapper
public interface TelegramNoSequenceMapper {

    /**
     * 전송일자의 마지막 전문번호를 1 올린다. 그날 첫 번호면 1로 행을 만든다.
     */
    int upsertNextTelegramNo(LocalDate sendDate);

    int findLastTelegramNo(LocalDate sendDate);
}
