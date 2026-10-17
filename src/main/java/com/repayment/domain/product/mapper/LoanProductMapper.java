package com.repayment.domain.product.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface LoanProductMapper {

    Optional<BigDecimal> findAdditionalRateById(Long loanProductId);
}
