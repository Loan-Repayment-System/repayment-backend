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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("집금 요청 매퍼")
class CollectionRequestMapperIntegrationTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 16, 14, 30, 15);
    private static final LocalDateTime NEXT_RETRY_AT = LocalDateTime.of(2026, 10, 16, 14, 31, 15);

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
        @DisplayName("(대출, 고객 상환, 요청, 10/16, 전문번호 1, 14:30:15, 999 / 200-000-000001, 30,000원, 다음 시도 14:31:15)을 넣으면 그 값 그대로 1행이 생기고 시도 횟수는 0, 만든 id가 DTO에 채워진다")
        void 넘긴_값_그대로_1행을_넣고_id를_채운다() {
            // given
            CollectionRequestResponse request = requested(1);

            // when
            collectionRequestMapper.insertCollectionRequest(request);

            // then
            assertThat(request.getCollectionRequestId()).isNotNull();
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT loan_account_id, repayment_channel_id, "
                    + "collection_request_status_id, send_date, telegram_no, sent_at, withdrawal_bank_code, "
                    + "withdrawal_account_no, amount, retry_count, next_retry_at FROM collection_request "
                    + "WHERE collection_request_id = ?",
                    request.getCollectionRequestId());
            assertThat(((Number) row.get("loan_account_id")).longValue()).isEqualTo(loanAccountId);
            assertThat(((Number) row.get("repayment_channel_id")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("collection_request_status_id")).intValue()).isEqualTo(1);
            assertThat(row.get("send_date").toString()).isEqualTo("2026-10-16");
            assertThat(((Number) row.get("telegram_no")).intValue()).isEqualTo(1);
            assertThat(row.get("sent_at")).isEqualTo(SENT_AT);
            assertThat(row.get("withdrawal_bank_code")).isEqualTo("999");
            assertThat(row.get("withdrawal_account_no")).isEqualTo("200-000-000001");
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("30000");
            assertThat(((Number) row.get("retry_count")).intValue()).isZero();
            assertThat(row.get("next_retry_at")).isEqualTo(NEXT_RETRY_AT);
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

    @Nested
    @DisplayName("처리 중인 집금 요청이 있는지 확인할 때")
    class 처리_중인_집금_요청_확인 {

        @Test
        @DisplayName("대출에 집금 요청이 없으면 false다")
        void 집금_요청이_없으면_false() {
            // when
            boolean exists = collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("요청 상태 1건이 있으면 true다")
        void 요청_상태가_있으면_true() {
            // given
            insertWithStatus(loanAccountId, 1, CollectionRequestStatus.REQUESTED);

            // when
            boolean exists = collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("집금 완료 상태 1건이 있으면 true다")
        void 집금_완료_상태가_있으면_true() {
            // given
            insertWithStatus(loanAccountId, 1, CollectionRequestStatus.COLLECTED);

            // when
            boolean exists = collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("상환 완료 1건, 거절 1건만 있으면 false다")
        void 끝난_건만_있으면_false() {
            // given
            insertWithStatus(loanAccountId, 1, CollectionRequestStatus.COMPLETED);
            insertWithStatus(loanAccountId, 2, CollectionRequestStatus.FAILED);

            // when
            boolean exists = collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("다른 대출에 요청 상태 1건이 있어도 이 대출은 false다")
        void 다른_대출의_건은_보지_않는다() {
            // given
            long otherContractId = insert("INSERT INTO loan_contract (loan_product_id, member_id, contract_date, "
                    + "maturity_date, contract_principal, contract_additional_rate, contract_base_rate, "
                    + "total_installments) SELECT loan_product_id, member_id, contract_date, maturity_date, "
                    + "contract_principal, contract_additional_rate, contract_base_rate, total_installments "
                    + "FROM loan_contract WHERE loan_contract_id = (SELECT loan_contract_id FROM loan_account "
                    + "WHERE loan_account_id = ?)", loanAccountId);
            long otherLoanAccountId = insert("INSERT INTO loan_account (loan_contract_id, repayment_bank_code, "
                    + "repayment_account_no, account_no, loan_status_id, loan_principal_balance, monthly_payment_day) "
                    + "VALUES (?, '999', '200-000-000002', '900-000-000402', 1, 10000000, 10)", otherContractId);
            insertWithStatus(otherLoanAccountId, 1, CollectionRequestStatus.REQUESTED);

            // when
            boolean exists = collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId);

            // then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("재요청 대상을 조회할 때")
    class 재요청_대상_조회 {

        @Test
        @DisplayName("다음 시도 14:31:15인 요청 상태 1건을 14:31:15에 조회하면 그 1건의 모든 값을 가져온다")
        void 시각이_된_건을_가져온다() {
            // given
            Long id = insertWithNextRetryAt(1, CollectionRequestStatus.REQUESTED, NEXT_RETRY_AT);

            // when
            List<CollectionRequestResponse> targets = collectionRequestMapper.findRetryTargets(NEXT_RETRY_AT);

            // then
            assertThat(targets).hasSize(1);
            CollectionRequestResponse target = targets.get(0);
            assertThat(target.getCollectionRequestId()).isEqualTo(id);
            assertThat(target.getLoanAccountId()).isEqualTo(loanAccountId);
            assertThat(target.getRepaymentChannelId()).isEqualTo(RepaymentChannel.CUSTOMER);
            assertThat(target.getCollectionRequestStatusId()).isEqualTo(CollectionRequestStatus.REQUESTED);
            assertThat(target.getSendDate()).isEqualTo(LocalDate.of(2026, 10, 16));
            assertThat(target.getTelegramNo()).isEqualTo(1);
            assertThat(target.getSentAt()).isEqualTo(SENT_AT);
            assertThat(target.getWithdrawalBankCode()).isEqualTo("999");
            assertThat(target.getWithdrawalAccountNo()).isEqualTo("200-000-000001");
            assertThat(target.getAmount()).isEqualByComparingTo("30000");
            assertThat(target.getRetryCount()).isZero();
            assertThat(target.getNextRetryAt()).isEqualTo(NEXT_RETRY_AT);
        }

        @Test
        @DisplayName("다음 시도 14:31:15인 요청 상태 1건을 14:31:14에 조회하면 0건이다")
        void 시각_전에는_가져오지_않는다() {
            // given
            insertWithNextRetryAt(1, CollectionRequestStatus.REQUESTED, NEXT_RETRY_AT);

            // when
            List<CollectionRequestResponse> targets =
                    collectionRequestMapper.findRetryTargets(NEXT_RETRY_AT.minusSeconds(1));

            // then
            assertThat(targets).isEmpty();
        }

        @Test
        @DisplayName("요청 · 집금 완료 · 상환 완료 · 거절 각 1건이 모두 시각이 되면 요청 · 집금 완료 2건만 가져온다")
        void 처리_중인_건만_가져온다() {
            // given
            insertWithNextRetryAt(1, CollectionRequestStatus.REQUESTED, NEXT_RETRY_AT);
            insertWithNextRetryAt(2, CollectionRequestStatus.COLLECTED, NEXT_RETRY_AT);
            insertWithNextRetryAt(3, CollectionRequestStatus.COMPLETED, NEXT_RETRY_AT);
            insertWithNextRetryAt(4, CollectionRequestStatus.FAILED, NEXT_RETRY_AT);

            // when
            List<CollectionRequestResponse> targets = collectionRequestMapper.findRetryTargets(NEXT_RETRY_AT);

            // then
            assertThat(targets).extracting(CollectionRequestResponse::getCollectionRequestStatusId)
                    .containsExactlyInAnyOrder(CollectionRequestStatus.REQUESTED, CollectionRequestStatus.COLLECTED);
        }

        @Test
        @DisplayName("다음 시도 14:33 · 14:32인 처리 중 2건은 14:32, 14:33 순으로 가져온다")
        void 다음_시도_시각이_이른_것부터_가져온다() {
            // given
            LocalDateTime later = LocalDateTime.of(2026, 10, 16, 14, 33);
            LocalDateTime earlier = LocalDateTime.of(2026, 10, 16, 14, 32);
            insertWithNextRetryAt(1, CollectionRequestStatus.REQUESTED, later);
            insertWithNextRetryAt(2, CollectionRequestStatus.COLLECTED, earlier);

            // when
            List<CollectionRequestResponse> targets = collectionRequestMapper.findRetryTargets(later);

            // then
            assertThat(targets).extracting(CollectionRequestResponse::getNextRetryAt)
                    .containsExactly(earlier, later);
        }
    }

    @Nested
    @DisplayName("재요청 시도를 기록할 때")
    class 재요청_시도_기록 {

        @Test
        @DisplayName("시도 0회인 건을 다음 시도 14:33:15로 기록하면 시도 횟수 1, 다음 시도 14:33:15다")
        void 시도_횟수를_올리고_다음_시도_시각을_바꾼다() {
            // given
            Long id = insertWithNextRetryAt(1, CollectionRequestStatus.REQUESTED, NEXT_RETRY_AT);
            LocalDateTime nextRetryAt = LocalDateTime.of(2026, 10, 16, 14, 33, 15);

            // when
            int updated = collectionRequestMapper.updateRetryAttempt(id, nextRetryAt);

            // then
            assertThat(updated).isEqualTo(1);
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT retry_count, next_retry_at "
                    + "FROM collection_request WHERE collection_request_id = ?", id);
            assertThat(((Number) row.get("retry_count")).intValue()).isEqualTo(1);
            assertThat(row.get("next_retry_at")).isEqualTo(nextRetryAt);
        }
    }

    private void insertWithStatus(long loanAccountId, int telegramNo, CollectionRequestStatus status) {
        collectionRequestMapper.insertCollectionRequest(request(loanAccountId, telegramNo, status, NEXT_RETRY_AT));
    }

    private Long insertWithNextRetryAt(int telegramNo, CollectionRequestStatus status, LocalDateTime nextRetryAt) {
        CollectionRequestResponse request = request(loanAccountId, telegramNo, status, nextRetryAt);
        collectionRequestMapper.insertCollectionRequest(request);
        return request.getCollectionRequestId();
    }

    private CollectionRequestResponse requested(int telegramNo) {
        return request(loanAccountId, telegramNo, CollectionRequestStatus.REQUESTED, NEXT_RETRY_AT);
    }

    private static CollectionRequestResponse request(long loanAccountId, int telegramNo,
                                                     CollectionRequestStatus status, LocalDateTime nextRetryAt) {
        return CollectionRequestResponse.builder()
                .loanAccountId(loanAccountId)
                .repaymentChannelId(RepaymentChannel.CUSTOMER)
                .collectionRequestStatusId(status)
                .sendDate(LocalDate.of(2026, 10, 16))
                .telegramNo(telegramNo)
                .sentAt(SENT_AT)
                .withdrawalBankCode("999")
                .withdrawalAccountNo("200-000-000001")
                .amount(new BigDecimal("30000"))
                .nextRetryAt(nextRetryAt)
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
