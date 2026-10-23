package com.repayment.api.loan;

import com.repayment.domain.loan.dto.response.LoanAccountDetailResponse;
import com.repayment.domain.loan.service.LoanAccountDetailService;
import com.repayment.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoanAccountController {

    private final LoanAccountDetailService loanAccountDetailService;

    @GetMapping("/api/loans/{loanAccountId}")
    public ApiResponse<LoanAccountDetailResponse> getDetail(@PathVariable Long loanAccountId) {
        return ApiResponse.ok(loanAccountDetailService.getDetail(loanAccountId));
    }
}
