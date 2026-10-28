package com.repayment.domain.repayment.service;

import com.repayment.domain.closing.mapper.DailyClosingMapper;
import com.repayment.domain.loan.dto.response.RepaymentLoanAccountResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.InvalidRepaymentAmountException;
import com.repayment.domain.repayment.exception.NothingToRepayException;
import com.repayment.domain.repayment.exception.RepaymentExceedsRepayableAmountException;
import com.repayment.domain.repayment.exception.RepaymentUnavailableDuringClosingException;
import com.repayment.global.code.DailyClosingStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("상환 가능 확인")
class RepaymentEligibilityServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final String REPAYMENT_BANK_CODE = "999";
    private static final String REPAYMENT_ACCOUNT_NO = "110-123-456789";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock
    private DailyClosingMapper dailyClosingMapper;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private RepayableAmountService repayableAmountService;

    @InjectMocks
    private RepaymentEligibilityService repaymentEligibilityService;

    @Nested
    @DisplayName("오늘 일마감 상태를 볼 때")
    class 일마감_상태 {

        @Test
        @DisplayName("오늘 10/16 일마감 행이 없으면 일마감 중이라고 거절하고 대출을 잠그지 않는다")
        void 일마감_행이_없으면_거절한다() {
            // given
            given(dailyClosingMapper.findStatusByBusinessDate(TODAY)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(RepaymentUnavailableDuringClosingException.class);
            then(loanAccountMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("오늘 10/16 일마감이 진행중이면 일마감 중이라고 거절하고 대출을 잠그지 않는다")
        void 일마감이_진행중이면_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.IN_PROGRESS);

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(RepaymentUnavailableDuringClosingException.class);
            then(loanAccountMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("오늘 10/16 일마감이 실패면 일마감 중이라고 거절하고 대출을 잠그지 않는다")
        void 일마감이_실패면_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.FAILED);

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(RepaymentUnavailableDuringClosingException.class);
            then(loanAccountMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("오늘 10/16 일마감이 완료고 갚을 금액이 5,000원이면 다음 검사로 넘어가 통과한다")
        void 일마감이_완료면_통과한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.NORMAL);
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "5000");

            // when
            RepaymentEligibilityResponse result =
                    repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY);

            // then
            assertThat(result.getRepayableAmount().getTotalAmount()).isEqualByComparingTo("5000");
        }
    }

    @Nested
    @DisplayName("대출을 잠글 때")
    class 대출_잠금 {

        // CUST-06
        @Test
        @DisplayName("대출 행이 없으면 대출을 찾을 수 없다고 거절한다")
        void 없는_대출은_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            given(loanAccountMapper.findRepaymentLoanAccountForUpdate(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(LoanAccountNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상환 금액을 검사할 때")
    class 금액_검사 {

        // CUST-03, PAY-06
        @Test
        @DisplayName("0원이면 금액이 올바르지 않다고 거절한다")
        void 영원은_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("0"), TODAY))
                    .isInstanceOf(InvalidRepaymentAmountException.class);
        }

        // CUST-03, PAY-06
        @Test
        @DisplayName("-1,000원이면 금액이 올바르지 않다고 거절한다")
        void 음수는_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("-1000"), TODAY))
                    .isInstanceOf(InvalidRepaymentAmountException.class);
        }

        // CUST-03, PAY-06
        @Test
        @DisplayName("1,000.5원이면 원 단위가 아니라 금액이 올바르지 않다고 거절한다")
        void 원_미만_자릿수는_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000.5"), TODAY))
                    .isInstanceOf(InvalidRepaymentAmountException.class);
        }

        @Test
        @DisplayName("1,000.00원이고 갚을 금액이 5,000원이면 원 단위로 보고 통과한다")
        void 영으로_끝나는_소수는_원_단위로_본다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.NORMAL);
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "5000");

            // when
            RepaymentEligibilityResponse result =
                    repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000.00"), TODAY);

            // then
            assertThat(result.getRepayableAmount().getTotalAmount()).isEqualByComparingTo("5000");
        }
    }

    @Nested
    @DisplayName("가장 최근 판정을 볼 때")
    class 가장_최근_판정 {

        // CUST-06
        @Test
        @DisplayName("2 완제면 이미 완제됐다고 거절하고 갚을 금액을 계산하지 않는다")
        void 완제는_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.PAID_OFF);

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(LoanAlreadyPaidOffException.class);
            then(repayableAmountService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("판정 행이 없고 갚을 금액이 5,000원이면 0 정상으로 갚을 금액을 계산하고 판정 0 정상으로 통과한다")
        void 판정이_없으면_0_정상으로_본다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "5000");

            // when
            RepaymentEligibilityResponse result =
                    repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY);

            // then
            assertThat(result.getLatestJudgment()).isEqualTo(LoanJudgmentStatus.NORMAL);
            then(repayableAmountService).should().calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
        }
    }

    @Nested
    @DisplayName("갚을 금액과 비교할 때")
    class 갚을_금액_비교 {

        // CUST-06
        @Test
        @DisplayName("0 정상인데 갚을 금액이 0원이면 갚을 금액이 없다고 거절한다")
        void 갚을_금액이_0원이면_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.NORMAL);
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "0");

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1000"), TODAY))
                    .isInstanceOf(NothingToRepayException.class);
        }

        // CUST-05, PAY-07
        @Test
        @DisplayName("갚을 금액 5,000원에 5,001원을 내면 갚을 금액보다 많다고 거절하고 응답 data에 5,000원을 담는다")
        void 일원이라도_초과하면_거절한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.NORMAL);
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "5000");

            // when & then
            assertThatThrownBy(() -> repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("5001"), TODAY))
                    .isInstanceOfSatisfying(RepaymentExceedsRepayableAmountException.class,
                            e -> assertThat((BigDecimal) e.getData()).isEqualByComparingTo("5000"));
        }

        @Test
        @DisplayName("갚을 금액 5,000원에 5,000원을 내면 통과한다")
        void 딱_맞게_내면_통과한다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.NORMAL);
            givenRepayableAmount(LoanJudgmentStatus.NORMAL, "5000");

            // when
            RepaymentEligibilityResponse result =
                    repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("5000"), TODAY);

            // then
            assertThat(result.getRepayableAmount().getTotalAmount()).isEqualByComparingTo("5000");
        }

        // PAY-06 부분 상환
        @Test
        @DisplayName("21 1회 연체에서 갚을 금액 5,000원 중 1원만 내면 통과하고 상환 계좌 999 110-123-456789, 판정 21, 갚을 금액 내역을 그대로 돌려준다")
        void 부분_상환은_통과하고_상환_처리가_쓸_값을_돌려준다() {
            // given
            givenDailyClosing(DailyClosingStatus.COMPLETED);
            givenLoanAccount();
            givenLatestJudgment(LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            RepayableAmountResponse repayableAmount =
                    givenRepayableAmount(LoanJudgmentStatus.INSTALLMENT_OVERDUE, "5000");

            // when
            RepaymentEligibilityResponse result =
                    repaymentEligibilityService.check(LOAN_ACCOUNT_ID, new BigDecimal("1"), TODAY);

            // then
            assertThat(result.getRepaymentBankCode()).isEqualTo(REPAYMENT_BANK_CODE);
            assertThat(result.getRepaymentAccountNo()).isEqualTo(REPAYMENT_ACCOUNT_NO);
            assertThat(result.getLatestJudgment()).isEqualTo(LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            assertThat(result.getRepayableAmount()).isSameAs(repayableAmount);
        }
    }

    private void givenDailyClosing(DailyClosingStatus status) {
        given(dailyClosingMapper.findStatusByBusinessDate(TODAY)).willReturn(Optional.of(status));
    }

    private void givenLoanAccount() {
        given(loanAccountMapper.findRepaymentLoanAccountForUpdate(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(new RepaymentLoanAccountResponse(LOAN_ACCOUNT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO)));
    }

    private void givenLatestJudgment(LoanJudgmentStatus status) {
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID)).willReturn(Optional.of(status));
    }

    // 0원이면 빈 목록, 아니면 그 금액의 회차 원금 1개
    private RepayableAmountResponse givenRepayableAmount(LoanJudgmentStatus status, String totalAmount) {
        BigDecimal amount = new BigDecimal(totalAmount);
        List<RepaymentTargetResponse> targets = amount.signum() == 0
                ? List.of()
                : List.of(new RepaymentTargetResponse(1, RepaymentTargetType.INSTALLMENT_PRINCIPAL, 13L, amount));
        RepayableAmountResponse response = new RepayableAmountResponse(amount, targets);
        given(repayableAmountService.calculate(LOAN_ACCOUNT_ID, status)).willReturn(response);
        return response;
    }
}
