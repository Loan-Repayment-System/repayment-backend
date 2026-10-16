package com.repayment.domain.delinquency.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 회차 연체금(installment_delinquency_charge 행)이 없다.
 */
public class InstallmentDelinquencyChargeNotFoundException extends BusinessException {

    private final Long installmentDelinquencyChargeId;

    public InstallmentDelinquencyChargeNotFoundException(Long installmentDelinquencyChargeId) {
        super(ErrorCode.INSTALLMENT_DELINQUENCY_CHARGE_NOT_FOUND);
        this.installmentDelinquencyChargeId = installmentDelinquencyChargeId;
    }

    public Long getInstallmentDelinquencyChargeId() {
        return installmentDelinquencyChargeId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " installmentDelinquencyChargeId=" + installmentDelinquencyChargeId;
    }
}
