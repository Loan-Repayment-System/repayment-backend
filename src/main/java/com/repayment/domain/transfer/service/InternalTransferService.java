package com.repayment.domain.transfer.service;

import com.repayment.domain.transfer.dto.response.AccountTransactionResponse;
import com.repayment.domain.transfer.dto.response.TransferAccountResponse;
import com.repayment.domain.transfer.exception.AccountNotFoundException;
import com.repayment.domain.transfer.exception.InsufficientBalanceException;
import com.repayment.domain.transfer.mapper.AccountMapper;
import com.repayment.domain.transfer.mapper.AccountTransactionMapper;
import com.repayment.global.code.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 당행 계좌에서 당행 계좌로 금액을 옮기고 출금·입금 거래 2행을 남긴다.
 * 부르는 쪽 트랜잭션에 함께 들어가므로, 부르는 쪽이 취소하면 이체도 취소된다.
 * 계좌 상태와 금액 형식은 보지 않는다. 금액은 부르는 쪽(상환 가능 확인)이 검사한다.
 */
@Service
@RequiredArgsConstructor
public class InternalTransferService {

    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;

    @Transactional
    public void transfer(Long withdrawalAccountId, Long depositAccountId, BigDecimal amount, String transactionNo) {
        // A→B와 B→A가 동시에 와도 데드락이 나지 않게 account_id가 작은 계좌부터 잠근다.
        TransferAccountResponse withdrawal;
        TransferAccountResponse deposit;
        if (withdrawalAccountId < depositAccountId) {
            withdrawal = lock(withdrawalAccountId);
            deposit = lock(depositAccountId);
        } else {
            deposit = lock(depositAccountId);
            withdrawal = lock(withdrawalAccountId);
        }
        if (withdrawal.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(withdrawal.getAccountId(), withdrawal.getBalance(), amount);
        }

        BigDecimal withdrawalBalanceAfter = withdrawal.getBalance().subtract(amount);
        BigDecimal depositBalanceAfter = deposit.getBalance().add(amount);
        LocalDateTime transactedAt = LocalDateTime.now();
        accountMapper.updateBalance(withdrawal.getAccountId(), withdrawalBalanceAfter);
        accountMapper.updateBalance(deposit.getAccountId(), depositBalanceAfter);
        accountTransactionMapper.insertTransaction(transaction(withdrawal, TransactionType.WITHDRAWAL, amount,
                withdrawalBalanceAfter, deposit.getAccountNo(), transactionNo, transactedAt));
        accountTransactionMapper.insertTransaction(transaction(deposit, TransactionType.DEPOSIT, amount,
                depositBalanceAfter, withdrawal.getAccountNo(), transactionNo, transactedAt));
    }

    private TransferAccountResponse lock(Long accountId) {
        return accountMapper.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private AccountTransactionResponse transaction(TransferAccountResponse account, TransactionType type,
                                                   BigDecimal amount, BigDecimal balanceAfter,
                                                   String counterpartAccountNo, String transactionNo,
                                                   LocalDateTime transactedAt) {
        return AccountTransactionResponse.builder()
                .accountId(account.getAccountId())
                .transactionTypeId(type)
                .transactionNo(transactionNo)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .counterpartAccountNo(counterpartAccountNo)
                .transactedAt(transactedAt)
                .build();
    }
}
