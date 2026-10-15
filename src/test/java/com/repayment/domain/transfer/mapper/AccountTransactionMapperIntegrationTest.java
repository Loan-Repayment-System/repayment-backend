package com.repayment.domain.transfer.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.transfer.dto.response.AccountTransactionResponse;
import com.repayment.global.code.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("계좌 거래 기록 매퍼")
class AccountTransactionMapperIntegrationTest {

    private static final LocalDateTime TRANSACTED_AT = LocalDateTime.of(2026, 10, 15, 10, 30, 0);

    @Autowired
    private AccountTransactionMapper accountTransactionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long accountId;

    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('transfer-test', 'pw', '테스트')");
        accountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                + "VALUES (?, '100-001', 100000, 1)", memberId);
    }

    @Nested
    @DisplayName("거래 기록을 남길 때")
    class 거래_기록 {

        @Test
        @DisplayName("출금 30,000원(거래 후 70,000원, 상대 999-001, 거래번호 TX-1)을 남기면 유형 id 1과 넣은 값이 그대로 저장된다")
        void 넣은_값_그대로_저장한다() {
            // when
            accountTransactionMapper.insertTransaction(withdrawal("TX-1"));

            // then
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT transaction_type_id, transaction_no, amount, balance_after, counterpart_account_no, transacted_at "
                            + "FROM account_transaction WHERE account_id = ?", accountId);
            assertThat(row.get("transaction_type_id")).isEqualTo(1);
            assertThat(row.get("transaction_no")).isEqualTo("TX-1");
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("30000");
            assertThat((BigDecimal) row.get("balance_after")).isEqualByComparingTo("70000");
            assertThat(row.get("counterpart_account_no")).isEqualTo("999-001");
            assertThat(row.get("transacted_at")).isEqualTo(TRANSACTED_AT);
        }

        // 같은 이체가 두 번 기록되지 않는 마지막 안전장치
        @Test
        @DisplayName("같은 계좌에 거래번호 TX-1 행이 이미 있으면 같은 거래번호로 다시 남길 때 DuplicateKeyException이 난다")
        void 같은_계좌_같은_거래번호는_거절된다() {
            // given
            accountTransactionMapper.insertTransaction(withdrawal("TX-1"));

            // when & then
            assertThatThrownBy(() -> accountTransactionMapper.insertTransaction(withdrawal("TX-1")))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    private AccountTransactionResponse withdrawal(String transactionNo) {
        return AccountTransactionResponse.builder()
                .accountId(accountId)
                .transactionTypeId(TransactionType.WITHDRAWAL)
                .transactionNo(transactionNo)
                .amount(new BigDecimal("30000"))
                .balanceAfter(new BigDecimal("70000"))
                .counterpartAccountNo("999-001")
                .transactedAt(TRANSACTED_AT)
                .build();
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
