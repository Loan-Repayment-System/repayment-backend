package com.repayment.batch.reader;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

/**
 * 정한 횟수만큼 커넥션 획득에 실패한 뒤 실제 DataSource로 넘기는 테스트용 DataSource.
 * 커넥션 풀 고갈처럼 커넥션을 얻지 못하는 상황을 만든다.
 */
class ConnectionFailingDataSource extends DelegatingDataSource {

    private int remainingFailures;
    private int connectionAttempts;

    ConnectionFailingDataSource(DataSource target) {
        super(target);
    }

    void failNextConnections(int count) {
        remainingFailures = count;
    }

    int getConnectionAttempts() {
        return connectionAttempts;
    }

    @Override
    public Connection getConnection() throws SQLException {
        connectionAttempts++;
        if (remainingFailures > 0) {
            remainingFailures--;
            throw new SQLTransientConnectionException("테스트용 커넥션 획득 실패");
        }
        return super.getConnection();
    }
}
