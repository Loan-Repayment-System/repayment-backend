package com.repayment.batch.reader;

import com.repayment.global.code.LoanJudgmentStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.SingleColumnRowMapper;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 일마감 Chunk Step의 Reader. 모두 대출 ID를 loan_account_id 순으로 100개씩 읽는다.
 * JdbcPagingItemReader는 두 번째 페이지부터 "loan_account_id > 마지막으로 읽은 ID"로 읽어(keyset 페이징)
 * 처리 중 대상이 바뀌어도 건너뛰는 대출이 없다.
 */
@Configuration
public class DailyClosingReaderConfig {

    private static final int PAGE_SIZE = 100;

    // 가장 최근 판정(가장 큰 loan_status_judgment_id)이 완제가 아닌 대출. 판정이 없는 새 대출은 정상으로 본다.
    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> scheduleJudgmentTargetReader(DataSource dataSource) {
        return new JdbcPagingItemReaderBuilder<Long>()
                .name("scheduleJudgmentTargetReader")
                .dataSource(dataSource)
                .pageSize(PAGE_SIZE)
                .selectClause("SELECT la.loan_account_id")
                .fromClause("FROM loan_account la")
                .whereClause("WHERE COALESCE((SELECT lsj.loan_judgment_status_id"
                        + " FROM loan_status_judgment lsj"
                        + " WHERE lsj.loan_account_id = la.loan_account_id"
                        + " ORDER BY lsj.loan_status_judgment_id DESC"
                        + " LIMIT 1), :normal) <> :paidOff")
                .sortKeys(Map.of("loan_account_id", Order.ASCENDING))
                .parameterValues(Map.of(
                        "normal", LoanJudgmentStatus.NORMAL.getId(),
                        "paidOff", LoanJudgmentStatus.PAID_OFF.getId()))
                .rowMapper(new SingleColumnRowMapper<>(Long.class))
                .build();
    }
}
