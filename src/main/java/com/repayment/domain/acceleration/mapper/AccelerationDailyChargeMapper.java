package com.repayment.domain.acceleration.mapper;

import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeResponse;
import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeTargetResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Optional;

@Mapper
public interface AccelerationDailyChargeMapper {

    Optional<AccelerationDailyChargeTargetResponse> findAccrualTarget(Long loanAccountId);

    boolean existsByLoanAccelerationIdAndChargeDate(@Param("loanAccelerationId") Long loanAccelerationId,
                                                    @Param("chargeDate") LocalDate chargeDate);

    int insertCharge(AccelerationDailyChargeResponse charge);
}
