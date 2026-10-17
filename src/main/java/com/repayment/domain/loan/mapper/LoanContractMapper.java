package com.repayment.domain.loan.mapper;

import com.repayment.domain.loan.dto.response.LoanContractResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoanContractMapper {

    int insertLoanContract(LoanContractResponse loanContract);
}
