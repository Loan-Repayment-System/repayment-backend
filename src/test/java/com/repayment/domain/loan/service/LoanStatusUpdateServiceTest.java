package com.repayment.domain.loan.service;

import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 계좌 상태 갱신")
class LoanStatusUpdateServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @InjectMocks
    private LoanStatusUpdateService loanStatusUpdateService;

    @Test
    @DisplayName("오늘 판정이 0 정상이면 고객 상태를 정상으로 바꾼다")
    void 정상_판정은_정상이다() {
        // given
        LoanJudgmentStatus todayJudgment = LoanJudgmentStatus.NORMAL;

        // when
        loanStatusUpdateService.update(LOAN_ACCOUNT_ID, todayJudgment);

        // then
        then(loanAccountMapper).should().updateLoanStatus(LOAN_ACCOUNT_ID, LoanStatus.NORMAL);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = LoanJudgmentStatus.class, names = {"BULLET_INTEREST_OVERDUE", "INSTALLMENT_OVERDUE"})
    @DisplayName("오늘 판정이 11 이자연체·21 1회 연체면 상환 방법과 상관없이 고객 상태를 연체로 바꾼다")
    void 회차_연체_판정은_연체다(LoanJudgmentStatus todayJudgment) {
        // given: todayJudgment

        // when
        loanStatusUpdateService.update(LOAN_ACCOUNT_ID, todayJudgment);

        // then
        then(loanAccountMapper).should().updateLoanStatus(LOAN_ACCOUNT_ID, LoanStatus.OVERDUE);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = LoanJudgmentStatus.class, names = {
            "BULLET_ACCELERATION", "BULLET_ACCELERATION_CLAUSE_6",
            "INSTALLMENT_ACCELERATION", "INSTALLMENT_ACCELERATION_CLAUSE_6"})
    @DisplayName("오늘 판정이 12·13·22·23 기한이익상실이면 ❻항 여부와 상관없이 고객 상태를 기한이익상실로 바꾼다")
    void 기한이익상실_판정은_기한이익상실이다(LoanJudgmentStatus todayJudgment) {
        // given: todayJudgment

        // when
        loanStatusUpdateService.update(LOAN_ACCOUNT_ID, todayJudgment);

        // then
        then(loanAccountMapper).should().updateLoanStatus(LOAN_ACCOUNT_ID, LoanStatus.ACCELERATED);
    }

    @Test
    @DisplayName("오늘 판정이 1 만기 경과면 고객 상태를 만기 경과로 바꾼다")
    void 만기_경과_판정은_만기_경과다() {
        // given
        LoanJudgmentStatus todayJudgment = LoanJudgmentStatus.MATURITY_OVERDUE;

        // when
        loanStatusUpdateService.update(LOAN_ACCOUNT_ID, todayJudgment);

        // then
        then(loanAccountMapper).should().updateLoanStatus(LOAN_ACCOUNT_ID, LoanStatus.MATURITY_OVERDUE);
    }

    @Test
    @DisplayName("오늘 판정이 2 완제면 고객 상태를 완제로 바꾼다")
    void 완제_판정은_완제다() {
        // given
        LoanJudgmentStatus todayJudgment = LoanJudgmentStatus.PAID_OFF;

        // when
        loanStatusUpdateService.update(LOAN_ACCOUNT_ID, todayJudgment);

        // then
        then(loanAccountMapper).should().updateLoanStatus(LOAN_ACCOUNT_ID, LoanStatus.PAID_OFF);
    }
}
