package com.repayment.batch.reader;

import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.retry.support.RetryTemplate;

/**
 * 페이지 조회가 일시적 DB 오류로 실패하면 같은 페이지를 다시 조회하는 JdbcPagingItemReader.
 * Spring Batch의 fault tolerant retry는 Reader에 적용되지 않아 Reader가 스스로 다시 시도한다.
 *
 * <p>read()가 아니라 doReadPage()를 재정의해 다시 시도한다. read()를 다시 시도하면 첫 페이지 조회가 실패한 뒤
 * 빈 버퍼를 이미 읽은 페이지로 보고 null(데이터 끝)을 돌려, Step이 0건으로 완료된다.
 * 페이지 번호와 마지막 키는 doReadPage()가 성공해야 바뀌므로, 다시 조회해도 같은 페이지를 읽는다.
 */
public class RetryingJdbcPagingItemReader<T> extends JdbcPagingItemReader<T> {

    private final RetryTemplate retryTemplate;

    public RetryingJdbcPagingItemReader(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    @Override
    protected void doReadPage() {
        retryTemplate.execute(context -> {
            super.doReadPage();
            return null;
        });
    }
}
