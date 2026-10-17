package com.repayment.domain.transfer.mapper;

import com.repayment.domain.transfer.dto.response.TransferAccountResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface AccountMapper {

    Optional<TransferAccountResponse> findByIdForUpdate(Long accountId);

    Optional<Long> findAccountIdByAccountNo(String accountNo);

    boolean existsByAccountIdAndMemberId(@Param("accountId") Long accountId, @Param("memberId") Long memberId);

    int updateBalance(@Param("accountId") Long accountId, @Param("balance") BigDecimal balance);
}
