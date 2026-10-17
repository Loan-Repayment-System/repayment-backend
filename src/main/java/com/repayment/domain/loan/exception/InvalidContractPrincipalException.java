package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 약정금액이 1원 미만이거나 원 단위 정수가 아니다.
 */
public class InvalidContractPrincipalException extends BusinessException {

    private final BigDecimal contractPrincipal;

    public InvalidContractPrincipalException(BigDecimal contractPrincipal) {
        super(ErrorCode.INVALID_CONTRACT_PRINCIPAL);
        this.contractPrincipal = contractPrincipal;
    }

    public BigDecimal getContractPrincipal() {
        return contractPrincipal;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " contractPrincipal=" + contractPrincipal;
    }
}
