package com.repayment.domain.transfer.mapper;

import com.repayment.domain.transfer.dto.response.AccountTransactionResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountTransactionMapper {

    int insertTransaction(AccountTransactionResponse transaction);
}
