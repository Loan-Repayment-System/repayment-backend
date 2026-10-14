package com.repayment.domain.loan.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface LoanAccountMapper {

    Optional<BigDecimal> findRemainingDebtTotal(Long loanAccountId);
}
