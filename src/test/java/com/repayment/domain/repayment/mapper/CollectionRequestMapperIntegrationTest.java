package com.repayment.domain.repayment.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.RepaymentChannel;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("집금 요청 매퍼")
class CollectionRequestMapperIntegrationTest {

    @Autowired
    private CollectionRequestMapper collectionRequestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanAccountId;

    @BeforeEach
    void setUp() {
        long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('collection-test', 'pw', '테스트')");
        long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                + "VALUES (1, 2, '테스트 상품', 1.5)");
        long contractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, maturity_date, "
                + "contract_principal, contract_additional_rate, contract_base_rate, total_installments) "
                + "VALUES (?, ?, '2026-01-10', '2027-01-10', 10000000, 1.5, 4.5, 12)", productId, memberId);
        loanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_bank_code, repayment_account_no, "
                + "account_no, loan_status_id, loan_principal_balance, monthly_payment_day) "
                + "VALUES (?, '999', '200-000-000001', '900-000-000401', 1, 10000000, 10)", contractId);
    }

    @Nested
    @DisplayName("집금 요청을 저장할 때")
    class 집금_요청_저장 {

        @Test
        @DisplayName("(대출, 고객 상환, 요청, 10/16, 전문번호 1, 14:30:15, 999 / 200-000-000001, 30,000원)을 넣으면 그 값 그대로 1행이 생기고 만든 id가 DTO에 채워진다")
        void 넘긴_값_그대로_1행을_넣고_id를_채운다() {
            // given
            CollectionRequestResponse request = requested(1);

            // when
            collectionRequestMapper.insertCollectionRequest(request);

            // then
            assertThat(request.getCollectionRequestId()).isNotNull();
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT loan_account_id, repayment_channel_id, "
                    + "collection_request_status_id, send_date, telegram_no, sent_at, withdrawal_bank_code, "
                    + "withdrawal_account_no, amount FROM collection_request WHERE collection_request_id = ?",
                    request.getCollectionRequestId());
            assertThat(((Number) row.get("loan_account_id")).longValue()).isEqualTo(loanAccountId);
            assertThat(((Number) row.get("repayment_channel_id")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("collection_request_status_id")).intValue()).isEqualTo(1);
            assertThat(row.get("send_date").toString()).isEqualTo("2026-10-16");
            assertThat(((Number) row.get("telegram_no")).intValue()).isEqualTo(1);
            assertThat(row.get("sent_at")).isEqualTo(LocalDateTime.of(2026, 10, 16, 14, 30, 15));
            assertThat(row.get("withdrawal_bank_code")).isEqualTo("999");
            assertThat(row.get("withdrawal_account_no")).isEqualTo("200-000-000001");
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("상태를 바꿀 때")
    class 상태_변경 {

        @Test
        @DisplayName("요청 상태인 행을 요청 → 집금 완료로 바꾸면 1행이 바뀌고 상태는 집금 완료다")
        void 직전_상태가_맞으면_바꾼다() {
            // given
            CollectionRequestResponse request = requested(1);
            collectionRequestMapper.insertCollectionRequest(request);

            // when
            int updated = collectionRequestMapper.updateCollectionRequestStatus(request.getCollectionRequestId(),
                    CollectionRequestStatus.REQUESTED, CollectionRequestStatus.COLLECTED);

            // then
            assertThat(updated).isEqualTo(1);
            assertThat(findStatusId(request.getCollectionRequestId())).isEqualTo(2);
        }

        @Test
        @DisplayName("이미 집금 완료인 행을 다시 요청 → 집금 완료로 바꾸면 0행이 바뀌고 상태는 집금 완료 그대로다")
        void 이미_바뀐_행은_건드리지_않는다() {
            // given
            CollectionRequestResponse request = requested(1);
            collectionRequestMapper.insertCollectionRequest(request);
            collectionRequestMapper.updateCollectionRequestStatus(request.getCollectionRequestId(),
                    CollectionRequestStatus.REQUESTED, CollectionRequestStatus.COLLECTED);

            // when
            int updated = collectionRequestMapper.updateCollectionRequestStatus(request.getCollectionRequestId(),
                    CollectionRequestStatus.REQUESTED, CollectionRequestStatus.COLLECTED);

            // then
            assertThat(updated).isZero();
            assertThat(findStatusId(request.getCollectionRequestId())).isEqualTo(2);
        }
    }

    private CollectionRequestResponse requested(int telegramNo) {
        return CollectionRequestResponse.builder()
                .loanAccountId(loanAccountId)
                .repaymentChannelId(RepaymentChannel.CUSTOMER)
                .collectionRequestStatusId(CollectionRequestStatus.REQUESTED)
                .sendDate(LocalDate.of(2026, 10, 16))
                .telegramNo(telegramNo)
                .sentAt(LocalDateTime.of(2026, 10, 16, 14, 30, 15))
                .withdrawalBankCode("999")
                .withdrawalAccountNo("200-000-000001")
                .amount(new BigDecimal("30000"))
                .build();
    }

    private int findStatusId(Long collectionRequestId) {
        return jdbcTemplate.queryForObject("SELECT collection_request_status_id FROM collection_request "
                + "WHERE collection_request_id = ?", Integer.class, collectionRequestId);
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
