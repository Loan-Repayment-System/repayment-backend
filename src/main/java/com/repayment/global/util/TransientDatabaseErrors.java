package com.repayment.global.util;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

/**
 * 다시 시도하면 나아질 수 있는 DB 오류. 커넥션 획득 실패, 연결 끊김, 잠금 대기 초과, 데드락이 여기에 든다.
 * 자동 이체 상환 Reader·Step과 집금 요청 저장이 같은 기준으로 다시 시도한다.
 */
public final class TransientDatabaseErrors {

    public static final List<Class<? extends Throwable>> TYPES = List.of(
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class);

    private TransientDatabaseErrors() {
    }

    public static boolean matches(Throwable e) {
        return TYPES.stream().anyMatch(type -> type.isInstance(e));
    }
}
