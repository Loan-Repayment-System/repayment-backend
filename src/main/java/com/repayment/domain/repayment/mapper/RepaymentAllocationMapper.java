package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.RepaymentAllocationResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RepaymentAllocationMapper {

    int insertAllocation(RepaymentAllocationResponse allocation);
}
