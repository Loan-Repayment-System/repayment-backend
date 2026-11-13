package com.repayment.api.closing;

import com.repayment.batch.launcher.DailyClosingJobRunner;
import com.repayment.domain.closing.dto.response.DailyClosingRunResponse;
import com.repayment.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DailyClosingController {

    private final DailyClosingJobRunner dailyClosingJobRunner;

    @PostMapping("/api/batch/daily-closing")
    public ApiResponse<DailyClosingRunResponse> run() {
        return ApiResponse.ok(dailyClosingJobRunner.runManually());
    }
}
