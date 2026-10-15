package com.repayment.domain.maturity.service;

import com.repayment.domain.delinquency.service.DelinquencyChargeCalculator;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeResponse;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeTargetResponse;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.maturity.mapper.MaturityOverdueDailyChargeMapper;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("만기 경과 연체금 쌓기")
class MaturityOverdueDailyChargeServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long LOAN_MATURITY_OVERDUE_ID = 60L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 1, 12);

    @Mock
    private MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;

    @Spy
    private DelinquencyChargeCalculator delinquencyChargeCalculator;

    @InjectMocks
    private MaturityOverdueDailyChargeService maturityOverdueDailyChargeService;

    @Nested
    @DisplayName("그날 아직 쌓지 않았을 때")
    class 그날_아직_쌓지_않음 {

        // 적용 금리 4.5 + 1.5 = 6% → 연체 이자율 9%
        @Test
        @DisplayName("원금잔액 10,000,000원, 적용 금리 6%면 연체 이자율 9%로 연체금 2,465원을 근거 1 만기 경과, '진행중'으로 쌓는다")
        void 원금잔액에_연체금을_쌓는다() {
            // given
            givenTarget("10000000", "4.5", "1.5");

            // when
            maturityOverdueDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            MaturityOverdueDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getLoanMaturityOverdueId()).isEqualTo(LOAN_MATURITY_OVERDUE_ID);
            assertThat(charge.getChargeDate()).isEqualTo(BUSINESS_DATE);
            assertThat(charge.getLoanJudgmentStatusId()).isEqualTo(LoanJudgmentStatus.MATURITY_OVERDUE);
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("10000000");
            assertThat(charge.getDelinquencyRate()).isEqualByComparingTo("9");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("2465");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.IN_PROGRESS);
        }

        // 적용 금리 14.5 + 1.5 = 16% → 15% 이상이라 2%p를 더해 18%
        @Test
        @DisplayName("원금잔액 10,000,000원, 기준 14.5%·가산 1.5%면 적용 금리 16%에 2%p를 더한 18%로 연체금 4,931원을 쌓는다")
        void 기준금리와_가산금리를_더해_연체_이자율을_정한다() {
            // given
            givenTarget("10000000", "14.5", "1.5");

            // when
            maturityOverdueDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            MaturityOverdueDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyRate()).isEqualByComparingTo("18");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("4931");
        }

        @Test
        @DisplayName("원금은 다 내고 옮긴 이자만 남아 원금잔액이 0원이면 연체금 0원을 '해소'로 쌓는다")
        void 연체금이_0원이면_해소로_쌓는다() {
            // given
            givenTarget("0", "4.5", "1.5");

            // when
            maturityOverdueDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            MaturityOverdueDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("0");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("0");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.RESOLVED);
        }
    }

    @Nested
    @DisplayName("그날 이미 쌓았을 때")
    class 그날_이미_쌓음 {

        @Test
        @DisplayName("기준일 행이 있으면 아무것도 쌓지 않는다")
        void 아무것도_쌓지_않는다() {
            // given
            given(maturityOverdueDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(target("10000000", "4.5", "1.5")));
            given(maturityOverdueDailyChargeMapper.existsByLoanMaturityOverdueIdAndChargeDate(
                    LOAN_MATURITY_OVERDUE_ID, BUSINESS_DATE)).willReturn(true);

            // when
            maturityOverdueDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(maturityOverdueDailyChargeMapper).should(never()).insertCharge(any());
        }
    }

    @Nested
    @DisplayName("부르면 안 되는 상황일 때")
    class 잘못_부름 {

        @Test
        @DisplayName("전환 기록이 없으면 LoanMaturityOverdueNotFoundException을 던지고 그날 행 확인도 기록도 하지 않는다")
        void 전환_기록이_없으면_예외를_던진다() {
            // given
            given(maturityOverdueDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(
                    () -> maturityOverdueDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE));

            // then
            assertThat(thrown).isInstanceOf(LoanMaturityOverdueNotFoundException.class);
            then(maturityOverdueDailyChargeMapper).should(never())
                    .existsByLoanMaturityOverdueIdAndChargeDate(anyLong(), any());
            then(maturityOverdueDailyChargeMapper).should(never()).insertCharge(any());
        }
    }

    private void givenTarget(String principalBalance, String baseRate, String additionalRate) {
        given(maturityOverdueDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(target(principalBalance, baseRate, additionalRate)));
        given(maturityOverdueDailyChargeMapper.existsByLoanMaturityOverdueIdAndChargeDate(
                LOAN_MATURITY_OVERDUE_ID, BUSINESS_DATE)).willReturn(false);
    }

    private MaturityOverdueDailyChargeTargetResponse target(String principalBalance, String baseRate,
                                                            String additionalRate) {
        return MaturityOverdueDailyChargeTargetResponse.builder()
                .loanMaturityOverdueId(LOAN_MATURITY_OVERDUE_ID)
                .appliedBaseRate(new BigDecimal(baseRate))
                .appliedAdditionalRate(new BigDecimal(additionalRate))
                .loanPrincipalBalance(new BigDecimal(principalBalance))
                .build();
    }

    private MaturityOverdueDailyChargeResponse capturedCharge() {
        ArgumentCaptor<MaturityOverdueDailyChargeResponse> captor =
                ArgumentCaptor.forClass(MaturityOverdueDailyChargeResponse.class);
        then(maturityOverdueDailyChargeMapper).should().insertCharge(captor.capture());
        return captor.getValue();
    }
}
