package com.repayment.domain.loan.service;

import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 완제 확인")
class LoanPayoffServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @InjectMocks
    private LoanPayoffService loanPayoffService;

    @Nested
    @DisplayName("대출이 있을 때")
    class 대출이_있음 {

        @Test
        @DisplayName("남은 채무 합계가 0원이면 다 갚은 것이다")
        void 남은_채무가_0원이면_완제다() {
            // given
            given(loanAccountMapper.findRemainingDebtTotal(LOAN_ACCOUNT_ID)).willReturn(Optional.of(new BigDecimal("0.0000")));

            // when
            boolean result = loanPayoffService.isPaidOff(LOAN_ACCOUNT_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("남은 채무 합계가 연체금 3,000원뿐이어도 다 갚은 것이 아니다")
        void 연체금만_남아도_완제가_아니다() {
            // given
            given(loanAccountMapper.findRemainingDebtTotal(LOAN_ACCOUNT_ID)).willReturn(Optional.of(new BigDecimal("3000")));

            // when
            boolean result = loanPayoffService.isPaidOff(LOAN_ACCOUNT_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("대출이 없을 때")
    class 대출이_없음 {

        @Test
        @DisplayName("대출 ID를 담은 LoanAccountNotFoundException을 던진다")
        void 없는_대출은_예외를_던진다() {
            // given
            given(loanAccountMapper.findRemainingDebtTotal(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> loanPayoffService.isPaidOff(LOAN_ACCOUNT_ID));

            // then
            assertThat(thrown).isInstanceOfSatisfying(LoanAccountNotFoundException.class,
                    e -> assertThat(e.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID));
        }
    }
}
