package com.repayment.domain.schedule.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 회차 번호가 마지막 확정 회차 번호 + 1이 아니다. 앞 회차가 미확정이거나 이미 확정한 회차다.
 */
public class InstallmentConfirmOrderException extends BusinessException {

    private final Long loanAccountId;
    private final int installmentNo;
    private final int lastConfirmedInstallmentNo;

    public InstallmentConfirmOrderException(Long loanAccountId, int installmentNo, int lastConfirmedInstallmentNo) {
        super(ErrorCode.INVALID_INSTALLMENT_CONFIRM_ORDER);
        this.loanAccountId = loanAccountId;
        this.installmentNo = installmentNo;
        this.lastConfirmedInstallmentNo = lastConfirmedInstallmentNo;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public int getLastConfirmedInstallmentNo() {
        return lastConfirmedInstallmentNo;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", installmentNo=" + installmentNo
                + ", lastConfirmedInstallmentNo=" + lastConfirmedInstallmentNo;
    }
}
