package com.repayment.api.repayment;

import com.repayment.domain.repayment.dto.request.CreateRepaymentRequest;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 은행원이 고객 대신 상환한다. 그래서 요청한 사람이 대출 주인인지 보지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;

    @PostMapping("/api/loans/{loanAccountId}/repayments")
    public ApiResponse<RepaymentResultResponse> repay(@PathVariable Long loanAccountId,
                                                      @RequestBody @Valid CreateRepaymentRequest request) {
        return ApiResponse.ok(repaymentService.repay(loanAccountId, request.getAmount(), RepaymentChannel.CUSTOMER));
    }
}
