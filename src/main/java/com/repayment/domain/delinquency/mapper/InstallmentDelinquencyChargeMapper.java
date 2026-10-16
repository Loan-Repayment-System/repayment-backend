package com.repayment.domain.delinquency.mapper;

import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeResponse;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeTargetResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface InstallmentDelinquencyChargeMapper {

    List<InstallmentDelinquencyChargeTargetResponse> findAccrualTargets(@Param("loanAccountId") Long loanAccountId,
                                                                        @Param("chargeDate") LocalDate chargeDate);

    int insertCharge(InstallmentDelinquencyChargeResponse charge);

    Optional<Long> findRepaymentScheduleId(Long installmentDelinquencyChargeId);

    int updateAddPaidDelinquencyCharge(@Param("installmentDelinquencyChargeId") Long installmentDelinquencyChargeId,
                                       @Param("amount") BigDecimal amount);
}
