package com.repayment.batch.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 대기 시간은 검증 대상이 아니라 대기 없이 돌린다. 재시도 횟수와 대상 예외는 설정 그대로 쓴다.
@DisplayName("자동 이체 상환 Reader 재시도 설정")
class AutoDebitRepaymentReaderConfigTest {

    private RetryTemplate retryTemplate;

    @BeforeEach
    void setUp() {
        retryTemplate = new AutoDebitRepaymentReaderConfig().autoDebitTargetReadRetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
    }

    @Nested
    @DisplayName("다시 조회하면 나아질 수 있는 DB 오류일 때")
    class 재시도_대상 {

        // 실패 대처 case 1
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.repayment.batch.reader.AutoDebitRepaymentReaderConfigTest#transientFailures")
        @DisplayName("계속 실패하면 3번 시도한 뒤 그 예외를 던진다")
        void 세_번_시도한다(String name, RuntimeException failure) {
            // given
            AtomicInteger attempts = new AtomicInteger();

            // when
            Throwable thrown = catchFailure(attempts, failure);

            // then
            assertThat(thrown).isSameAs(failure);
            assertThat(attempts).hasValue(3);
        }
    }

    @Nested
    @DisplayName("다시 조회해도 같은 오류가 나는 DB 오류일 때")
    class 재시도_대상_아님 {

        // 실패 대처 case 1
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.repayment.batch.reader.AutoDebitRepaymentReaderConfigTest#permanentFailures")
        @DisplayName("다시 시도하지 않고 1번 만에 그 예외를 던진다")
        void 한_번만_시도한다(String name, RuntimeException failure) {
            // given
            AtomicInteger attempts = new AtomicInteger();

            // when
            Throwable thrown = catchFailure(attempts, failure);

            // then
            assertThat(thrown).isSameAs(failure);
            assertThat(attempts).hasValue(1);
        }
    }

    static Stream<Arguments> transientFailures() {
        return Stream.of(
                Arguments.of("쿼리 중 연결 끊김", new RecoverableDataAccessException("연결 끊김")),
                Arguments.of("쿼리 시간 초과", new QueryTimeoutException("시간 초과")),
                Arguments.of("커넥션 획득 실패", new CannotGetJdbcConnectionException("커넥션 획득 실패")),
                Arguments.of("MAX_EXECUTION_TIME 초과로 중단", mysqlError(3024)));
    }

    static Stream<Arguments> permanentFailures() {
        return Stream.of(
                Arguments.of("분류되지 않은 다른 MySQL 오류", mysqlError(9999)),
                Arguments.of("SQL 문법 오류", new BadSqlGrammarException("read", "SELECT", new SQLException("문법 오류"))));
    }

    private static UncategorizedSQLException mysqlError(int errorCode) {
        return new UncategorizedSQLException("read", "SELECT", new SQLException("MySQL 오류", "HY000", errorCode));
    }

    private Throwable catchFailure(AtomicInteger attempts, RuntimeException failure) {
        try {
            retryTemplate.execute(context -> {
                attempts.incrementAndGet();
                throw failure;
            });
        } catch (RuntimeException e) {
            return e;
        }
        return null;
    }
}
