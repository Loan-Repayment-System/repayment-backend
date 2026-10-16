package com.repayment.domain.transfer.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.transfer.dto.response.TransferAccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("계좌 매퍼")
class AccountMapperIntegrationTest {

    @Autowired
    private AccountMapper accountMapper;

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
    @DisplayName("계좌를 잠그고 읽을 때")
    class 잠그고_읽기 {

        @Test
        @DisplayName("계좌번호 100-001, 잔액 100,000원 계좌를 읽으면 id·번호·잔액이 담긴다")
        void 계좌의_id_번호_잔액을_읽는다() {
            // when
            Optional<TransferAccountResponse> result = accountMapper.findByIdForUpdate(accountId);

            // then
            assertThat(result).hasValueSatisfying(account -> {
                assertThat(account.getAccountId()).isEqualTo(accountId);
                assertThat(account.getAccountNo()).isEqualTo("100-001");
                assertThat(account.getBalance()).isEqualByComparingTo("100000");
            });
        }

        @Test
        @DisplayName("없는 id면 빈 값이다")
        void 없는_계좌면_빈_값이다() {
            // when
            Optional<TransferAccountResponse> result = accountMapper.findByIdForUpdate(accountId + 1);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("계좌번호로 계좌 id를 찾을 때")
    class 계좌번호로_찾기 {

        @Test
        @DisplayName("계좌번호 100-000-000001인 은행 계좌가 있으면 그 계좌 id를 돌려준다")
        void 있는_계좌번호면_id를_돌려준다() {
            // given
            long collectionAccountId = insert("INSERT INTO account (member_id, account_no, balance, account_status_id) "
                    + "VALUES (NULL, '100-000-000001', 0, 1)");

            // when
            Optional<Long> result = accountMapper.findAccountIdByAccountNo("100-000-000001");

            // then
            assertThat(result).contains(collectionAccountId);
        }

        @Test
        @DisplayName("계좌번호 100-000-000001인 계좌가 없으면 빈 값이다")
        void 없는_계좌번호면_빈_값이다() {
            // when
            Optional<Long> result = accountMapper.findAccountIdByAccountNo("100-000-000001");

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("잔액을 바꿀 때")
    class 잔액_변경 {

        @Test
        @DisplayName("잔액 100,000원 계좌를 70,000원으로 바꾸면 DB 잔액이 70,000원이다")
        void 잔액을_저장한다() {
            // when
            accountMapper.updateBalance(accountId, new BigDecimal("70000"));

            // then
            BigDecimal balance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?", BigDecimal.class, accountId);
            assertThat(balance).isEqualByComparingTo("70000");
        }
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
