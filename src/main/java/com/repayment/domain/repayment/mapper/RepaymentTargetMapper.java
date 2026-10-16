package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.UnpaidAccelerationResponse;
import com.repayment.domain.repayment.dto.response.UnpaidDailyChargeResponse;
import com.repayment.domain.repayment.dto.response.UnpaidInstallmentResponse;
import com.repayment.domain.repayment.dto.response.UnpaidMaturityOverdueResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RepaymentTargetMapper {

    List<UnpaidDailyChargeResponse> findUnpaidInstallmentDelinquencyCharges(Long loanAccountId);

    List<UnpaidInstallmentResponse> findUnpaidInstallments(Long loanAccountId);

    Optional<UnpaidAccelerationResponse> findUnpaidAcceleration(Long loanAccountId);

    List<UnpaidDailyChargeResponse> findUnpaidAccelerationDailyCharges(Long loanAccelerationId);

    Optional<UnpaidMaturityOverdueResponse> findUnpaidMaturityOverdue(Long loanAccountId);

    List<UnpaidDailyChargeResponse> findUnpaidMaturityOverdueDailyCharges(Long loanMaturityOverdueId);
}
