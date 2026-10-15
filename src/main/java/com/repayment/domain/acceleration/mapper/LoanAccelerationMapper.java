package com.repayment.domain.acceleration.mapper;

import com.repayment.domain.acceleration.dto.response.LoanAccelerationCarryOverResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface LoanAccelerationMapper {

    boolean existsByLoanAccountId(Long loanAccountId);

    LoanAccelerationCarryOverResponse findCarryOver(Long loanAccountId);

    int insertAcceleration(@Param("loanAccountId") Long loanAccountId,
                           @Param("convertedDate") LocalDate convertedDate,
                           @Param("carryOver") LoanAccelerationCarryOverResponse carryOver);
}
