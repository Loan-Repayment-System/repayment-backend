package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.InstallmentRepaymentResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InstallmentRepaymentMapper {

    int insertInstallmentRepayment(InstallmentRepaymentResponse installmentRepayment);
}
