package com.repayment.api.repayment;

import com.repayment.batch.launcher.AutoDebitRepaymentJobRunner;
import com.repayment.domain.repayment.dto.response.AutoDebitRepaymentRunResponse;
import com.repayment.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AutoDebitRepaymentController {

    private final AutoDebitRepaymentJobRunner autoDebitRepaymentJobRunner;

    @PostMapping("/api/batch/auto-debit-repayment")
    public ApiResponse<AutoDebitRepaymentRunResponse> run() {
        return ApiResponse.ok(autoDebitRepaymentJobRunner.runManually());
    }
}
