package com.repayment.domain.product.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 개설하려는 대출 상품이 없다.
 */
public class LoanProductNotFoundException extends BusinessException {

    private final Long loanProductId;

    public LoanProductNotFoundException(Long loanProductId) {
        super(ErrorCode.LOAN_PRODUCT_NOT_FOUND);
        this.loanProductId = loanProductId;
    }

    public Long getLoanProductId() {
        return loanProductId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanProductId=" + loanProductId;
    }
}
