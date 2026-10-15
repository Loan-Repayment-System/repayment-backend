package com.repayment.domain.schedule.mapper;

import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RepaymentScheduleMapper {

    Optional<RepaymentScheduleConfirmTargetResponse> findConfirmTarget(Long repaymentScheduleId);

    int updateConfirmation(@Param("repaymentScheduleId") Long repaymentScheduleId,
                           @Param("amount") RepaymentAmountResponse amount,
                           @Param("rate") AppliedRateResponse rate);

    int updateLastConfirmedInstallmentNo(@Param("loanAccountId") Long loanAccountId,
                                         @Param("installmentNo") int installmentNo);

    int updateStatus(@Param("repaymentScheduleId") Long repaymentScheduleId,
                     @Param("status") RepaymentScheduleStatus status);

    int updateUnpaidInstallmentStatus(@Param("loanAccountId") Long loanAccountId,
                                      @Param("status") RepaymentScheduleStatus status);
}
