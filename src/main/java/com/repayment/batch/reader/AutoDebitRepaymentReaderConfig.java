package com.repayment.batch.reader;

import com.repayment.global.code.AutoDebitAgreementStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.SingleColumnRowMapper;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Map;

/**
 * 자동 이체 상환 Step의 Reader. 대출 ID를 loan_account_id 순으로 100개씩 읽는다.
 */
@Configuration
public class AutoDebitRepaymentReaderConfig {

    private static final int PAGE_SIZE = 100;

    // 약정이 사용 중이고 기준일이 약정 기간 안이며, 가장 최근 판정이 완제가 아닌 대출. 판정이 없는 새 대출은 정상으로 본다.
    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> autoDebitRepaymentTargetReader(
            DataSource dataSource, @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
        return new JdbcPagingItemReaderBuilder<Long>()
                .name("autoDebitRepaymentTargetReader")
                .dataSource(dataSource)
                .pageSize(PAGE_SIZE)
                .selectClause("SELECT la.loan_account_id")
                .fromClause("FROM loan_account la")
                .whereClause("WHERE EXISTS (SELECT 1"
                        + " FROM auto_debit_agreement ada"
                        + " WHERE ada.loan_account_id = la.loan_account_id"
                        + " AND ada.auto_debit_agreement_status_id = :active"
                        + " AND ada.start_date <= :businessDate"
                        + " AND (ada.end_date IS NULL OR ada.end_date >= :businessDate))"
                        + " AND COALESCE((SELECT lsj.loan_judgment_status_id"
                        + " FROM loan_status_judgment lsj"
                        + " WHERE lsj.loan_account_id = la.loan_account_id"
                        + " ORDER BY lsj.loan_status_judgment_id DESC"
                        + " LIMIT 1), :normal) <> :paidOff")
                .sortKeys(Map.of("loan_account_id", Order.ASCENDING))
                .parameterValues(Map.of(
                        "active", AutoDebitAgreementStatus.ACTIVE.getId(),
                        "businessDate", businessDate,
                        "normal", LoanJudgmentStatus.NORMAL.getId(),
                        "paidOff", LoanJudgmentStatus.PAID_OFF.getId()))
                .rowMapper(new SingleColumnRowMapper<>(Long.class))
                .build();
    }
}
