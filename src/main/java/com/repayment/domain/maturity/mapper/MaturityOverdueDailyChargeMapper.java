package com.repayment.domain.maturity.mapper;

import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeResponse;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeTargetResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Optional;

@Mapper
public interface MaturityOverdueDailyChargeMapper {

    Optional<MaturityOverdueDailyChargeTargetResponse> findAccrualTarget(Long loanAccountId);

    boolean existsByLoanMaturityOverdueIdAndChargeDate(@Param("loanMaturityOverdueId") Long loanMaturityOverdueId,
                                                       @Param("chargeDate") LocalDate chargeDate);

    int insertCharge(MaturityOverdueDailyChargeResponse charge);
}
