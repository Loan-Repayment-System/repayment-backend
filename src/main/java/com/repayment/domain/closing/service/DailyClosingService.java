package com.repayment.domain.closing.service;

import com.repayment.domain.closing.exception.DailyClosingAlreadyCompletedException;
import com.repayment.domain.closing.exception.DailyClosingAlreadyRunningException;
import com.repayment.domain.closing.exception.DailyClosingNotRunningException;
import com.repayment.domain.closing.exception.PreviousDailyClosingNotCompletedException;
import com.repayment.domain.closing.mapper.DailyClosingMapper;
import com.repayment.global.code.DailyClosingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 기준일마다 일마감이 진행중·완료·실패인지 daily_closing 1행에 기록한다.
 * 처리 실패 기록이 없어 대출 하나라도 실패하면 그날 전체가 실패이므로, 완료된 날은 다시 실행하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DailyClosingService {

    private final DailyClosingMapper dailyClosingMapper;
    private final Clock clock;

    /**
     * 전날이 완료인지 확인하고 기준일 행을 '진행중'으로 만든다. 기준일보다 앞선 행이 없으면 첫 실행으로 본다.
     * 기준일 행이 없으면 만들고, '실패'면 다시 시작한다.
     */
    @Transactional
    public void start(LocalDate businessDate) {
        validatePreviousCompleted(businessDate);

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<DailyClosingStatus> status = dailyClosingMapper.findStatusByBusinessDate(businessDate);
        if (status.isEmpty()) {
            dailyClosingMapper.insertInProgress(businessDate, now);
            return;
        }
        switch (status.get()) {
            case FAILED -> restart(businessDate, now);
            case IN_PROGRESS -> throw new DailyClosingAlreadyRunningException(businessDate);
            case COMPLETED -> throw new DailyClosingAlreadyCompletedException(businessDate);
        }
    }

    /**
     * '진행중'인 기준일 행을 '완료'로 바꾼다. 이때부터 상환을 받는다.
     */
    @Transactional
    public void complete(LocalDate businessDate) {
        int updated = dailyClosingMapper.updateInProgressToFinished(
                businessDate, DailyClosingStatus.COMPLETED, LocalDateTime.now(clock));
        if (updated == 0) {
            throw new DailyClosingNotRunningException(businessDate);
        }
    }

    /**
     * '진행중'인 기준일 행을 '실패'로 바꾼다. 진행중이 아니면 아무것도 하지 않는다.
     * Job이 실패로 끝난 뒤 정리할 때 부르므로, 여기서 예외를 던져 원래 실패 원인을 가리지 않는다.
     */
    @Transactional
    public void fail(LocalDate businessDate) {
        dailyClosingMapper.updateInProgressToFinished(
                businessDate, DailyClosingStatus.FAILED, LocalDateTime.now(clock));
    }

    // 조회한 뒤 다른 실행이 먼저 '진행중'으로 바꿨으면 바뀐 행이 0이다.
    private void restart(LocalDate businessDate, LocalDateTime now) {
        if (dailyClosingMapper.updateFailedToInProgress(businessDate, now) == 0) {
            throw new DailyClosingAlreadyRunningException(businessDate);
        }
    }

    private void validatePreviousCompleted(LocalDate businessDate) {
        Optional<DailyClosingStatus> previous = dailyClosingMapper.findStatusByBusinessDate(businessDate.minusDays(1));
        if (previous.isEmpty()) {
            if (dailyClosingMapper.existsBeforeBusinessDate(businessDate)) {
                throw new PreviousDailyClosingNotCompletedException(businessDate);
            }
            return;
        }
        if (previous.get() != DailyClosingStatus.COMPLETED) {
            throw new PreviousDailyClosingNotCompletedException(businessDate);
        }
    }
}
