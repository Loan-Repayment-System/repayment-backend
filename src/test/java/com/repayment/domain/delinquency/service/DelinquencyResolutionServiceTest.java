package com.repayment.domain.delinquency.service;

import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.domain.maturity.mapper.MaturityOverdueDailyChargeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("연체금 해소 판정")
class DelinquencyResolutionServiceTest {

    private static final long LOAN_ACCOUNT_ID = 1L;

    @Mock
    private InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;

    @Mock
    private AccelerationDailyChargeMapper accelerationDailyChargeMapper;

    @Mock
    private MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;

    @InjectMocks
    private DelinquencyResolutionService delinquencyResolutionService;

    @Nested
    @DisplayName("대출 하나를 넘길 때")
    class 대출_하나 {

        @Test
        @DisplayName("대출 id 1을 넘기면 회차·기한이익상실·만기 경과 연체금 세 테이블의 해소를 모두 대출 id 1로 부른다")
        void 세_테이블의_해소를_모두_부른다() {
            // given
            // 대출 상태와 상관없이 세 테이블을 모두 본다

            // when
            delinquencyResolutionService.resolve(LOAN_ACCOUNT_ID);

            // then
            then(installmentDelinquencyChargeMapper).should().updateResolveFullyPaid(LOAN_ACCOUNT_ID);
            then(accelerationDailyChargeMapper).should().updateResolveFullyPaid(LOAN_ACCOUNT_ID);
            then(maturityOverdueDailyChargeMapper).should().updateResolveFullyPaid(LOAN_ACCOUNT_ID);
        }
    }
}
