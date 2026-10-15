package com.repayment.domain.maturity.mapper;

import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueCarryOverResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface LoanMaturityOverdueMapper {

    boolean existsByLoanAccountId(Long loanAccountId);

    LoanMaturityOverdueCarryOverResponse findCarryOver(Long loanAccountId);

    int insertMaturityOverdue(@Param("loanAccountId") Long loanAccountId,
                              @Param("convertedDate") LocalDate convertedDate,
                              @Param("carryOver") LoanMaturityOverdueCarryOverResponse carryOver);
}
