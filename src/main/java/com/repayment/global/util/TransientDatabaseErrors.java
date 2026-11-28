package com.repayment.global.util;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

/**
 * 다시 시도하면 나아질 수 있는 DB 오류. 커넥션 획득 실패, 연결 끊김, 잠금 대기 초과, 데드락, 쿼리 시간 초과가 여기에 든다.
 * 자동 이체 상환 Reader·Step과 집금 요청 저장이 같은 기준으로 다시 시도한다.
 */
public final class TransientDatabaseErrors {

    // Spring Batch Step의 retry는 예외 클래스로만 지정할 수 있어 이 목록을 쓴다.
    public static final List<Class<? extends Throwable>> TYPES = List.of(
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class);

    // MAX_EXECUTION_TIME 힌트를 넘겨 중단된 쿼리의 MySQL 오류 코드. Spring이 분류하지 못해 UncategorizedSQLException으로 온다.
    private static final int MYSQL_QUERY_TIMEOUT_ERROR_CODE = 3024;

    private TransientDatabaseErrors() {
    }

    public static boolean matches(Throwable e) {
        return TYPES.stream().anyMatch(type -> type.isInstance(e)) || isQueryTimeout(e);
    }

    private static boolean isQueryTimeout(Throwable e) {
        return e instanceof UncategorizedSQLException uncategorized
                && uncategorized.getSQLException() != null
                && uncategorized.getSQLException().getErrorCode() == MYSQL_QUERY_TIMEOUT_ERROR_CODE;
    }
}
