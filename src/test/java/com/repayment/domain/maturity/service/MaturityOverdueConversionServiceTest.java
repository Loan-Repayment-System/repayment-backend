package com.repayment.domain.maturity.service;

import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueCarryOverResponse;
import com.repayment.domain.maturity.mapper.LoanMaturityOverdueMapper;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("만기 경과 전환")
class MaturityOverdueConversionServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 1, 11);

    @Mock
    private LoanMaturityOverdueMapper loanMaturityOverdueMapper;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @InjectMocks
    private MaturityOverdueConversionService maturityOverdueConversionService;

    @Nested
    @DisplayName("아직 전환하지 않은 대출일 때")
    class 전환_전 {

        @Test
        @DisplayName("옮길 금액을 조회해 (대출, 2027-01-11, 조회한 금액)으로 기록한 뒤 회차를 만기 경과로 종료한다")
        void 금액을_옮긴_뒤_회차를_닫는다() {
            // given
            LoanMaturityOverdueCarryOverResponse carryOver = LoanMaturityOverdueCarryOverResponse.builder()
                    .appliedBaseRate(new BigDecimal("5.0"))
                    .appliedAdditionalRate(new BigDecimal("1.5"))
                    .carriedInterest(new BigDecimal("78000"))
                    .carriedDelinquencyCharge(new BigDecimal("500"))
                    .build();
            given(loanMaturityOverdueMapper.existsByLoanAccountId(LOAN_ACCOUNT_ID)).willReturn(false);
            given(loanMaturityOverdueMapper.findCarryOver(LOAN_ACCOUNT_ID)).willReturn(carryOver);

            // when
            maturityOverdueConversionService.convert(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanMaturityOverdueMapper, repaymentScheduleMapper);
            order.verify(loanMaturityOverdueMapper).findCarryOver(LOAN_ACCOUNT_ID);
            order.verify(loanMaturityOverdueMapper).insertMaturityOverdue(LOAN_ACCOUNT_ID, BUSINESS_DATE, carryOver);
            order.verify(repaymentScheduleMapper)
                    .updateUnpaidInstallmentStatus(LOAN_ACCOUNT_ID, RepaymentScheduleStatus.CLOSED_BY_MATURITY_OVERDUE);
        }
    }

    @Nested
    @DisplayName("이미 전환한 대출일 때")
    class 이미_전환함 {

        @Test
        @DisplayName("옮길 금액을 조회하지도, 기록하지도, 회차를 닫지도 않는다")
        void 아무것도_하지_않는다() {
            // given
            given(loanMaturityOverdueMapper.existsByLoanAccountId(LOAN_ACCOUNT_ID)).willReturn(true);

            // when
            maturityOverdueConversionService.convert(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(loanMaturityOverdueMapper).should(never()).findCarryOver(anyLong());
            then(loanMaturityOverdueMapper).should(never()).insertMaturityOverdue(anyLong(), any(), any());
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
        }
    }
}
