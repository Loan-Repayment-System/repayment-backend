package com.repayment.domain.acceleration.mapper;

import com.repayment.domain.acceleration.dto.response.LoanAccelerationCarryOverResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface LoanAccelerationMapper {

    boolean existsByLoanAccountId(Long loanAccountId);

    LoanAccelerationCarryOverResponse findCarryOver(Long loanAccountId);

    int insertAcceleration(@Param("loanAccountId") Long loanAccountId,
                           @Param("convertedDate") LocalDate convertedDate,
                           @Param("carryOver") LoanAccelerationCarryOverResponse carryOver);

    int updateAddPaidCarriedDelinquencyCharge(@Param("loanAccelerationId") Long loanAccelerationId,
                                              @Param("amount") BigDecimal amount);

    int updateAddPaidCarriedInterest(@Param("loanAccelerationId") Long loanAccelerationId,
                                     @Param("amount") BigDecimal amount);

    int updateAddPaidCarriedOverduePrincipal(@Param("loanAccelerationId") Long loanAccelerationId,
                                             @Param("amount") BigDecimal amount);
}
