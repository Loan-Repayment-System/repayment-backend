package com.repayment.domain.loan.mapper;

import com.repayment.global.code.LoanStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface LoanAccountMapper {

    Optional<BigDecimal> findRemainingDebtTotal(Long loanAccountId);

    int updateLoanStatus(@Param("loanAccountId") Long loanAccountId,
                         @Param("loanStatus") LoanStatus loanStatus);
}
