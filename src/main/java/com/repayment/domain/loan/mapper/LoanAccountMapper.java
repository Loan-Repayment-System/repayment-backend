package com.repayment.domain.loan.mapper;

import com.repayment.domain.loan.dto.response.LoanAccountResponse;
import com.repayment.domain.loan.dto.response.LoanAccountStatusResponse;
import com.repayment.domain.loan.dto.response.RepaymentLoanAccountResponse;
import com.repayment.global.code.LoanStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface LoanAccountMapper {

    Optional<RepaymentLoanAccountResponse> findRepaymentLoanAccountForUpdate(Long loanAccountId);

    Optional<LoanAccountStatusResponse> findLoanAccountStatus(Long loanAccountId);

    Optional<BigDecimal> findRemainingDebtTotal(Long loanAccountId);

    int insertLoanAccount(LoanAccountResponse loanAccount);

    int updateSubtractLoanPrincipalBalance(@Param("loanAccountId") Long loanAccountId,
                                           @Param("amount") BigDecimal amount);

    int updateLoanStatus(@Param("loanAccountId") Long loanAccountId,
                         @Param("loanStatus") LoanStatus loanStatus);
}
