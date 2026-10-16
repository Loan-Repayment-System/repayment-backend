package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RepaymentMapper {

    int insertRepayment(RepaymentResponse repayment);
}
