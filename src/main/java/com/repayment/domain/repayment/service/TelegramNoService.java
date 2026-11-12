package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.mapper.TelegramNoSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 펌뱅킹 전문번호를 전송일자마다 1부터 매긴다.
 * 부르는 쪽 트랜잭션과 따로 바로 커밋한다. 함께 묶으면 그 트랜잭션이 끝날 때까지 그날 행이 잠겨 모든 전송이 한 줄로 서고,
 * 롤백되면 이미 보낸 번호를 다시 내준다.
 */
@Service
@RequiredArgsConstructor
public class TelegramNoService {

    private final TelegramNoSequenceMapper telegramNoSequenceMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int next(LocalDate sendDate) {
        telegramNoSequenceMapper.upsertNextTelegramNo(sendDate);
        // 위 UPSERT가 행을 잠갔으므로 커밋 전까지 다른 트랜잭션이 바꾸지 못한다.
        return telegramNoSequenceMapper.findLastTelegramNo(sendDate);
    }
}
