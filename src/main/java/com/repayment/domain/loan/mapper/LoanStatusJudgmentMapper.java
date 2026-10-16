package com.repayment.domain.loan.mapper;

import com.repayment.domain.loan.dto.response.LoanStatusJudgmentResponse;
import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.global.code.LoanJudgmentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface LoanStatusJudgmentMapper {

    Optional<LoanStatusJudgmentTargetResponse> findJudgmentTarget(Long loanAccountId);

    Optional<LoanJudgmentStatus> findLatestJudgmentStatus(Long loanAccountId);

    List<LoanStatusJudgmentResponse> findRecentJudgments(Long loanAccountId);

    int insertJudgment(@Param("loanAccountId") Long loanAccountId,
                       @Param("businessDate") LocalDate businessDate,
                       @Param("status") LoanJudgmentStatus status);
}
