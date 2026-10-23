package com.repayment.domain.schedule.mapper;

import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.InstallmentDetailResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.dto.response.ScheduledInstallmentResponse;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RepaymentScheduleMapper {

    Optional<RepaymentScheduleConfirmTargetResponse> findConfirmTarget(Long repaymentScheduleId);

    List<RepaymentScheduleJudgmentTargetResponse> findJudgmentTargets(Long loanAccountId);

    List<RepaymentScheduleJudgmentTargetResponse> findUnfinishedJudgmentTargets(Long loanAccountId);

    List<InstallmentDetailResponse> findInstallmentDetails(Long loanAccountId);

    int insertScheduledInstallments(@Param("loanAccountId") Long loanAccountId,
                                    @Param("installments") List<ScheduledInstallmentResponse> installments);

    int updateConfirmation(@Param("repaymentScheduleId") Long repaymentScheduleId,
                           @Param("amount") RepaymentAmountResponse amount,
                           @Param("rate") AppliedRateResponse rate);

    int updateLastConfirmedInstallmentNo(@Param("loanAccountId") Long loanAccountId,
                                         @Param("installmentNo") int installmentNo);

    int updateStatus(@Param("repaymentScheduleId") Long repaymentScheduleId,
                     @Param("status") RepaymentScheduleStatus status);

    int updateAddPaidInterest(@Param("repaymentScheduleId") Long repaymentScheduleId,
                              @Param("amount") BigDecimal amount);

    int updateAddPaidPrincipal(@Param("repaymentScheduleId") Long repaymentScheduleId,
                               @Param("amount") BigDecimal amount);

    int updateUnpaidInstallmentStatus(@Param("loanAccountId") Long loanAccountId,
                                      @Param("status") RepaymentScheduleStatus status);
}
