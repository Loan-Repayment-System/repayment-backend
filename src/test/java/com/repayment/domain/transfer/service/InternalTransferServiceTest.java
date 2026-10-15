package com.repayment.domain.transfer.service;

import com.repayment.domain.transfer.dto.response.AccountTransactionResponse;
import com.repayment.domain.transfer.dto.response.TransferAccountResponse;
import com.repayment.domain.transfer.exception.AccountNotFoundException;
import com.repayment.domain.transfer.exception.InsufficientBalanceException;
import com.repayment.domain.transfer.mapper.AccountMapper;
import com.repayment.domain.transfer.mapper.AccountTransactionMapper;
import com.repayment.global.code.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("당행 이체")
class InternalTransferServiceTest {

    private static final String TRANSACTION_NO = "TX-1";

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountTransactionMapper accountTransactionMapper;

    @InjectMocks
    private InternalTransferService internalTransferService;

    @Nested
    @DisplayName("두 계좌를 잠글 때")
    class 잠그는_순서 {

        // 데드락 방지: 모든 이체가 같은 순서로 잠근다
        @Test
        @DisplayName("출금 계좌 id 1, 입금 계좌 id 2면 출금 계좌 1번을 먼저 잠근다")
        void 출금_계좌_id가_작으면_출금_계좌를_먼저_잠근다() {
            // given
            givenAccount(1L, "110-000-000001", "100000");
            givenAccount(2L, "999-000-000001", "50000");

            // when
            internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO);

            // then
            InOrder order = inOrder(accountMapper);
            order.verify(accountMapper).findByIdForUpdate(1L);
            order.verify(accountMapper).findByIdForUpdate(2L);
        }

        @Test
        @DisplayName("출금 계좌 id 2, 입금 계좌 id 1이면 입금 계좌 1번을 먼저 잠근다")
        void 입금_계좌_id가_작으면_입금_계좌를_먼저_잠근다() {
            // given
            givenAccount(2L, "110-000-000001", "100000");
            givenAccount(1L, "999-000-000001", "50000");

            // when
            internalTransferService.transfer(2L, 1L, new BigDecimal("30000"), TRANSACTION_NO);

            // then
            InOrder order = inOrder(accountMapper);
            order.verify(accountMapper).findByIdForUpdate(1L);
            order.verify(accountMapper).findByIdForUpdate(2L);
        }
    }

    @Nested
    @DisplayName("출금 계좌 잔액이 금액 이상일 때")
    class 잔액이_충분함 {

        @Test
        @DisplayName("출금 잔액 100,000원·입금 잔액 50,000원에서 30,000원을 옮기면 출금 잔액은 70,000원, 입금 잔액은 80,000원이 된다")
        void 두_계좌의_잔액을_바꾼다() {
            // given
            givenAccount(1L, "110-000-000001", "100000");
            givenAccount(2L, "999-000-000001", "50000");

            // when
            internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO);

            // then
            then(accountMapper).should().updateBalance(1L, new BigDecimal("70000"));
            then(accountMapper).should().updateBalance(2L, new BigDecimal("80000"));
        }

        @Test
        @DisplayName("30,000원을 옮기면 출금 행(거래 후 70,000원, 상대는 입금 계좌번호)과 입금 행(거래 후 80,000원, 상대는 출금 계좌번호)을 같은 거래번호·거래 일시로 남긴다")
        void 출금_입금_거래_2행을_남긴다() {
            // given
            givenAccount(1L, "110-000-000001", "100000");
            givenAccount(2L, "999-000-000001", "50000");

            // when
            internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO);

            // then
            ArgumentCaptor<AccountTransactionResponse> captor = ArgumentCaptor.forClass(AccountTransactionResponse.class);
            then(accountTransactionMapper).should(times(2)).insertTransaction(captor.capture());
            List<AccountTransactionResponse> transactions = captor.getAllValues();
            AccountTransactionResponse withdrawal = transactions.get(0);
            AccountTransactionResponse deposit = transactions.get(1);

            assertThat(withdrawal.getAccountId()).isEqualTo(1L);
            assertThat(withdrawal.getTransactionTypeId()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(withdrawal.getAmount()).isEqualByComparingTo("30000");
            assertThat(withdrawal.getBalanceAfter()).isEqualByComparingTo("70000");
            assertThat(withdrawal.getCounterpartAccountNo()).isEqualTo("999-000-000001");

            assertThat(deposit.getAccountId()).isEqualTo(2L);
            assertThat(deposit.getTransactionTypeId()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(deposit.getAmount()).isEqualByComparingTo("30000");
            assertThat(deposit.getBalanceAfter()).isEqualByComparingTo("80000");
            assertThat(deposit.getCounterpartAccountNo()).isEqualTo("110-000-000001");

            assertThat(withdrawal.getTransactionNo()).isEqualTo(TRANSACTION_NO);
            assertThat(deposit.getTransactionNo()).isEqualTo(TRANSACTION_NO);
            assertThat(withdrawal.getTransactedAt()).isNotNull().isEqualTo(deposit.getTransactedAt());
        }

        @Test
        @DisplayName("출금 잔액 30,000원에서 30,000원을 옮기면 출금 잔액은 0원이 된다")
        void 잔액과_금액이_같으면_0원이_된다() {
            // given
            givenAccount(1L, "110-000-000001", "30000");
            givenAccount(2L, "999-000-000001", "50000");

            // when
            internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO);

            // then
            then(accountMapper).should().updateBalance(1L, new BigDecimal("0"));
            then(accountMapper).should().updateBalance(2L, new BigDecimal("80000"));
        }
    }

    @Nested
    @DisplayName("이체할 수 없을 때")
    class 이체_불가 {

        @Test
        @DisplayName("출금 잔액 29,999원에서 30,000원을 옮기면 InsufficientBalanceException을 던지고 잔액·거래 기록을 바꾸지 않는다")
        void 잔액이_1원이라도_모자라면_옮기지_않는다() {
            // given
            givenAccount(1L, "110-000-000001", "29999");
            givenAccount(2L, "999-000-000001", "50000");

            // when & then
            assertThatThrownBy(() -> internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO))
                    .isInstanceOf(InsufficientBalanceException.class);
            thenNothingChanged();
        }

        @Test
        @DisplayName("출금 계좌가 없으면 출금 계좌 id를 담은 AccountNotFoundException을 던지고 아무것도 바꾸지 않는다")
        void 출금_계좌가_없으면_예외를_던진다() {
            // given
            given(accountMapper.findByIdForUpdate(1L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.getAccountId()).isEqualTo(1L));
            thenNothingChanged();
        }

        @Test
        @DisplayName("입금 계좌가 없으면 입금 계좌 id를 담은 AccountNotFoundException을 던지고 아무것도 바꾸지 않는다")
        void 입금_계좌가_없으면_예외를_던진다() {
            // given
            givenAccount(1L, "110-000-000001", "100000");
            given(accountMapper.findByIdForUpdate(2L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> internalTransferService.transfer(1L, 2L, new BigDecimal("30000"), TRANSACTION_NO))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.getAccountId()).isEqualTo(2L));
            thenNothingChanged();
        }

        private void thenNothingChanged() {
            then(accountMapper).should(never()).updateBalance(anyLong(), any());
            then(accountTransactionMapper).should(never()).insertTransaction(any());
        }
    }

    private void givenAccount(Long accountId, String accountNo, String balance) {
        given(accountMapper.findByIdForUpdate(accountId)).willReturn(Optional.of(TransferAccountResponse.builder()
                .accountId(accountId)
                .accountNo(accountNo)
                .balance(new BigDecimal(balance))
                .build()));
    }
}
