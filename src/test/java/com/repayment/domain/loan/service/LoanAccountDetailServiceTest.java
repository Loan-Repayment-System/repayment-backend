package com.repayment.domain.loan.service;

import com.repayment.domain.acceleration.dto.response.LoanAccelerationDetailResponse;
import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.acceleration.mapper.LoanAccelerationMapper;
import com.repayment.domain.loan.dto.response.LoanAccountDetailResponse;
import com.repayment.domain.loan.dto.response.LoanBasicInfoResponse;
import com.repayment.domain.loan.dto.response.LoanCurrentStatusResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueDetailResponse;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.maturity.mapper.LoanMaturityOverdueMapper;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.service.RepayableAmountService;
import com.repayment.domain.schedule.dto.response.InstallmentDetailResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 현황 조회")
class LoanAccountDetailServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private LoanAccelerationMapper loanAccelerationMapper;

    @Mock
    private LoanMaturityOverdueMapper loanMaturityOverdueMapper;

    @Mock
    private RepayableAmountService repayableAmountService;

    @InjectMocks
    private LoanAccountDetailService loanAccountDetailService;

    @Test
    @DisplayName("대출 행이 없으면 대출을 찾을 수 없다고 거절하고 다른 조회는 하지 않는다")
    void 없는_대출은_거절한다() {
        // given
        given(loanAccountMapper.findLoanAccountDetail(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID))
                .isInstanceOf(LoanAccountNotFoundException.class);
        then(repaymentScheduleMapper).shouldHaveNoInteractions();
        then(repayableAmountService).shouldHaveNoInteractions();
    }

    @Nested
    @DisplayName("정상·연체 대출일 때")
    class 정상_연체 {

        @Test
        @DisplayName("정상이고 판정 0이면 회차 목록과 판정 0으로 계산한 갚을 금액을 넣고 기한이익상실·만기 경과 기록은 넣지 않는다")
        void 정상은_회차_목록을_넣는다() {
            // given
            givenDetail(LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL);
            List<InstallmentDetailResponse> installments = givenInstallments();
            RepayableAmountResponse repayableAmount = givenRepayableAmount(LoanJudgmentStatus.NORMAL);

            // when
            LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

            // then
            assertThat(result.getInstallments()).isSameAs(installments);
            assertThat(result.getRepayableAmount()).isSameAs(repayableAmount);
            assertThat(result.getAcceleration()).isNull();
            assertThat(result.getMaturityOverdue()).isNull();
            then(loanAccelerationMapper).shouldHaveNoInteractions();
            then(loanMaturityOverdueMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("연체이고 판정 21 1회 연체면 회차 목록을 넣고 갚을 금액은 판정 21로 계산한다")
        void 연체는_최근_판정으로_갚을_금액을_계산한다() {
            // given
            givenDetail(LoanStatus.OVERDUE, LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            List<InstallmentDetailResponse> installments = givenInstallments();
            RepayableAmountResponse repayableAmount = givenRepayableAmount(LoanJudgmentStatus.INSTALLMENT_OVERDUE);

            // when
            LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

            // then
            assertThat(result.getInstallments()).isSameAs(installments);
            assertThat(result.getRepayableAmount()).isSameAs(repayableAmount);
        }

        @Test
        @DisplayName("개설 직후라 판정 기록이 없으면 최근 판정은 null로 두고 갚을 금액은 0 정상으로 계산한다")
        void 판정이_없으면_0_정상으로_계산한다() {
            // given
            givenDetail(LoanStatus.NORMAL, null);
            givenInstallments();
            givenRepayableAmount(LoanJudgmentStatus.NORMAL);

            // when
            LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

            // then
            assertThat(result.getStatus().getLatestJudgment()).isNull();
            then(repayableAmountService).should().calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
        }
    }

    @Nested
    @DisplayName("기한이익상실 대출일 때")
    class 기한이익상실 {

        @Test
        @DisplayName("판정 22면 기한이익상실 기록과 판정 22로 계산한 갚을 금액을 넣고 회차 목록은 조회하지 않는다")
        void 기한이익상실_기록을_넣는다() {
            // given
            givenDetail(LoanStatus.ACCELERATED, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            LoanAccelerationDetailResponse acceleration = LoanAccelerationDetailResponse.builder().build();
            given(loanAccelerationMapper.findAccelerationDetail(LOAN_ACCOUNT_ID)).willReturn(Optional.of(acceleration));
            RepayableAmountResponse repayableAmount =
                    givenRepayableAmount(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);

            // when
            LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

            // then
            assertThat(result.getAcceleration()).isSameAs(acceleration);
            assertThat(result.getRepayableAmount()).isSameAs(repayableAmount);
            assertThat(result.getInstallments()).isNull();
            assertThat(result.getMaturityOverdue()).isNull();
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("기한이익상실 기록이 없으면 LoanAccelerationNotFoundException을 던진다")
        void 기록이_없으면_예외를_던진다() {
            // given
            givenDetail(LoanStatus.ACCELERATED, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            given(loanAccelerationMapper.findAccelerationDetail(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID))
                    .isInstanceOf(LoanAccelerationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("만기 경과 대출일 때")
    class 만기_경과 {

        @Test
        @DisplayName("판정 1이면 만기 경과 기록과 판정 1로 계산한 갚을 금액을 넣고 회차 목록·기한이익상실 기록은 넣지 않는다")
        void 만기_경과_기록을_넣는다() {
            // given
            givenDetail(LoanStatus.MATURITY_OVERDUE, LoanJudgmentStatus.MATURITY_OVERDUE);
            LoanMaturityOverdueDetailResponse maturityOverdue = LoanMaturityOverdueDetailResponse.builder().build();
            given(loanMaturityOverdueMapper.findMaturityOverdueDetail(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(maturityOverdue));
            RepayableAmountResponse repayableAmount = givenRepayableAmount(LoanJudgmentStatus.MATURITY_OVERDUE);

            // when
            LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

            // then
            assertThat(result.getMaturityOverdue()).isSameAs(maturityOverdue);
            assertThat(result.getRepayableAmount()).isSameAs(repayableAmount);
            assertThat(result.getInstallments()).isNull();
            assertThat(result.getAcceleration()).isNull();
        }

        @Test
        @DisplayName("만기 경과 기록이 없으면 LoanMaturityOverdueNotFoundException을 던진다")
        void 기록이_없으면_예외를_던진다() {
            // given
            givenDetail(LoanStatus.MATURITY_OVERDUE, LoanJudgmentStatus.MATURITY_OVERDUE);
            given(loanMaturityOverdueMapper.findMaturityOverdueDetail(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID))
                    .isInstanceOf(LoanMaturityOverdueNotFoundException.class);
        }
    }

    @Test
    @DisplayName("완제면 기본 정보·현재 상태만 넣고 회차·기록 조회와 갚을 금액 계산을 하지 않는다")
    void 완제는_기본_정보와_상태만_넣는다() {
        // given
        LoanAccountDetailResponse detail = givenDetail(LoanStatus.PAID_OFF, LoanJudgmentStatus.PAID_OFF);

        // when
        LoanAccountDetailResponse result = loanAccountDetailService.getDetail(LOAN_ACCOUNT_ID);

        // then
        assertThat(result.getLoan()).isSameAs(detail.getLoan());
        assertThat(result.getStatus()).isSameAs(detail.getStatus());
        assertThat(result.getInstallments()).isNull();
        assertThat(result.getAcceleration()).isNull();
        assertThat(result.getMaturityOverdue()).isNull();
        assertThat(result.getRepayableAmount()).isNull();
        then(repaymentScheduleMapper).shouldHaveNoInteractions();
        then(loanAccelerationMapper).shouldHaveNoInteractions();
        then(loanMaturityOverdueMapper).shouldHaveNoInteractions();
        then(repayableAmountService).shouldHaveNoInteractions();
    }

    private LoanAccountDetailResponse givenDetail(LoanStatus loanStatus, LoanJudgmentStatus latestJudgment) {
        LoanAccountDetailResponse detail = LoanAccountDetailResponse.builder()
                .loan(LoanBasicInfoResponse.builder().loanAccountId(LOAN_ACCOUNT_ID).build())
                .status(new LoanCurrentStatusResponse(loanStatus, latestJudgment))
                .build();
        given(loanAccountMapper.findLoanAccountDetail(LOAN_ACCOUNT_ID)).willReturn(Optional.of(detail));
        return detail;
    }

    private List<InstallmentDetailResponse> givenInstallments() {
        List<InstallmentDetailResponse> installments = List.of(InstallmentDetailResponse.builder().installmentNo(1).build());
        given(repaymentScheduleMapper.findInstallmentDetails(LOAN_ACCOUNT_ID)).willReturn(installments);
        return installments;
    }

    private RepayableAmountResponse givenRepayableAmount(LoanJudgmentStatus latestJudgment) {
        RepayableAmountResponse repayableAmount = new RepayableAmountResponse(new BigDecimal("5000"), List.of());
        given(repayableAmountService.calculate(LOAN_ACCOUNT_ID, latestJudgment)).willReturn(repayableAmount);
        return repayableAmount;
    }
}
