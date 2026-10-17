package com.repayment.domain.loan.service;

import com.repayment.domain.baserate.exception.BaseRateNotFoundException;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import com.repayment.domain.loan.dto.response.LoanAccountResponse;
import com.repayment.domain.loan.dto.response.LoanContractResponse;
import com.repayment.domain.loan.exception.DisbursementAccountNotFoundException;
import com.repayment.domain.loan.exception.InvalidContractPrincipalException;
import com.repayment.domain.loan.exception.InvalidTotalInstallmentsException;
import com.repayment.domain.loan.exception.RepaymentAccountNotOwnedException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanContractMapper;
import com.repayment.domain.product.exception.LoanProductNotFoundException;
import com.repayment.domain.product.mapper.LoanProductMapper;
import com.repayment.domain.schedule.dto.response.ScheduledInstallmentResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.transfer.mapper.AccountMapper;
import com.repayment.domain.transfer.service.InternalTransferService;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.config.RepaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 은행원이 대출을 개설한다. 계약, 대출 계좌, 회차를 만들고 대출금을 상환 계좌로 지급한다.
 * 회차는 예정일만 정하고, 금액·금리는 예정일 5일 전에 상환금 확정이 채운다.
 */
@Service
@RequiredArgsConstructor
public class LoanOpeningService {

    private static final String LOAN_ACCOUNT_NO_PREFIX = "900-";

    private final LoanProductMapper loanProductMapper;
    private final AccountMapper accountMapper;
    private final BaseRateHistoryMapper baseRateHistoryMapper;
    private final LoanContractMapper loanContractMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final InternalTransferService internalTransferService;
    private final RepaymentProperties repaymentProperties;
    private final Clock clock;

    /**
     * 위에서부터 검사해 처음 걸린 것으로 거절하고, 모두 통과하면 한 트랜잭션으로 만든 뒤 대출 계좌 id를 돌려준다.
     */
    @Transactional
    public Long open(Long memberId, Long loanProductId, Long repaymentAccountId,
                     BigDecimal contractPrincipal, int totalInstallments) {
        BigDecimal additionalRate = loanProductMapper.findAdditionalRateById(loanProductId)
                .orElseThrow(() -> new LoanProductNotFoundException(loanProductId));
        if (!isValidPrincipal(contractPrincipal)) {
            throw new InvalidContractPrincipalException(contractPrincipal);
        }
        if (totalInstallments < 1) {
            throw new InvalidTotalInstallmentsException(totalInstallments);
        }
        if (!accountMapper.existsByAccountIdAndMemberId(repaymentAccountId, memberId)) {
            throw new RepaymentAccountNotOwnedException(repaymentAccountId, memberId);
        }

        LocalDate contractDate = LocalDate.now(clock);
        BigDecimal baseRate = baseRateHistoryMapper.findLatestRateBefore(contractDate)
                .orElseThrow(() -> new BaseRateNotFoundException(contractDate));
        String disbursementAccountNo = repaymentProperties.disbursementAccountNo();
        Long disbursementAccountId = accountMapper.findAccountIdByAccountNo(disbursementAccountNo)
                .orElseThrow(() -> new DisbursementAccountNotFoundException(disbursementAccountNo));

        LoanContractResponse contract = LoanContractResponse.builder()
                .loanProductId(loanProductId)
                .memberId(memberId)
                .contractDate(contractDate)
                .maturityDate(scheduledDate(contractDate, totalInstallments))
                .contractPrincipal(contractPrincipal)
                .contractAdditionalRate(additionalRate)
                .contractBaseRate(baseRate)
                .totalInstallments(totalInstallments)
                .build();
        loanContractMapper.insertLoanContract(contract);

        LoanAccountResponse loanAccount = LoanAccountResponse.builder()
                .loanContractId(contract.getLoanContractId())
                .repaymentAccountId(repaymentAccountId)
                .accountNo(loanAccountNo(contract.getLoanContractId()))
                .loanStatusId(LoanStatus.NORMAL)
                .loanPrincipalBalance(contractPrincipal)
                .monthlyPaymentDay(contractDate.getDayOfMonth())
                .build();
        loanAccountMapper.insertLoanAccount(loanAccount);

        repaymentScheduleMapper.insertScheduledInstallments(loanAccount.getLoanAccountId(),
                scheduledInstallments(contractDate, totalInstallments));

        internalTransferService.transfer(disbursementAccountId, repaymentAccountId, contractPrincipal,
                UUID.randomUUID().toString());
        return loanAccount.getLoanAccountId();
    }

    private List<ScheduledInstallmentResponse> scheduledInstallments(LocalDate contractDate, int totalInstallments) {
        return IntStream.rangeClosed(1, totalInstallments)
                .mapToObj(no -> new ScheduledInstallmentResponse(no, scheduledDate(contractDate, no)))
                .toList();
    }

    // 계약일에서 바로 k개월을 더한다. 앞 회차에서 이어 더하면 1/31 → 2/28 → 3/28처럼 말일이 밀린다.
    // 그 달에 계약일과 같은 날이 없으면 말일이다(1/31 계약 → 2/28).
    private LocalDate scheduledDate(LocalDate contractDate, int installmentNo) {
        return contractDate.plusMonths(installmentNo);
    }

    // 900- 뒤에 계약 id를 9자리로 채운다. 예) 123 → 900-000-000123
    private String loanAccountNo(Long loanContractId) {
        String digits = String.format("%09d", loanContractId);
        return LOAN_ACCOUNT_NO_PREFIX + digits.substring(0, 3) + "-" + digits.substring(3);
    }

    // 1원 이상이고 원 미만 자릿수가 없어야 한다. 1000.00처럼 0으로 끝나는 소수 자리는 원 단위로 본다.
    private boolean isValidPrincipal(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ONE) >= 0
                && amount.stripTrailingZeros().scale() <= 0;
    }
}
