package com.repayment.batch.reader;

import com.repayment.global.code.AutoDebitAgreementStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Map;

/**
 * 자동 이체 상환 Step의 Reader. 대출 ID를 loan_account_id 순으로 100개씩 읽는다.
 * 페이지 조회가 일시적 DB 오류로 실패하면 같은 페이지를 다시 조회한다.
 * JdbcPagingItemReader에는 쿼리 제한 시간 설정이 없어, MySQL 힌트 MAX_EXECUTION_TIME으로 DB가 쿼리를 중단하게 한다.
 */
@Configuration
public class AutoDebitRepaymentReaderConfig {

    private static final int PAGE_SIZE = 100;

    // 페이지 조회 한 번의 제한 시간
    private static final int QUERY_TIMEOUT_MILLIS = 10_000;

    // 약정이 사용 중이고 기준일이 약정 기간 안이며, 가장 최근 판정이 완제가 아닌 대출. 판정이 없는 새 대출은 정상으로 본다.
    @Bean
    @StepScope
    public RetryingJdbcPagingItemReader<Long> autoDebitRepaymentTargetReader(
            DataSource dataSource, RetryTemplate transientDatabaseRetryTemplate,
            @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT /*+ MAX_EXECUTION_TIME(" + QUERY_TIMEOUT_MILLIS + ") */ la.loan_account_id");
        queryProvider.setFromClause("FROM loan_account la");
        queryProvider.setWhereClause("WHERE EXISTS (SELECT 1"
                + " FROM auto_debit_agreement ada"
                + " WHERE ada.loan_account_id = la.loan_account_id"
                + " AND ada.auto_debit_agreement_status_id = :active"
                + " AND ada.start_date <= :businessDate"
                + " AND (ada.end_date IS NULL OR ada.end_date >= :businessDate))"
                + " AND COALESCE((SELECT lsj.loan_judgment_status_id"
                + " FROM loan_status_judgment lsj"
                + " WHERE lsj.loan_account_id = la.loan_account_id"
                + " ORDER BY lsj.loan_status_judgment_id DESC"
                + " LIMIT 1), :normal) <> :paidOff");
        queryProvider.setSortKeys(Map.of("loan_account_id", Order.ASCENDING));

        RetryingJdbcPagingItemReader<Long> reader = new RetryingJdbcPagingItemReader<>(transientDatabaseRetryTemplate);
        reader.setName("autoDebitRepaymentTargetReader");
        reader.setDataSource(dataSource);
        reader.setPageSize(PAGE_SIZE);
        reader.setQueryProvider(queryProvider);
        reader.setParameterValues(Map.of(
                "active", AutoDebitAgreementStatus.ACTIVE.getId(),
                "businessDate", businessDate,
                "normal", LoanJudgmentStatus.NORMAL.getId(),
                "paidOff", LoanJudgmentStatus.PAID_OFF.getId()));
        reader.setRowMapper(new SingleColumnRowMapper<>(Long.class));
        return reader;
    }
}
