package com.repayment.batch.processor;

import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.service.RepayableAmountService;
import com.repayment.global.code.LoanJudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("자동 이체 상환 Processor")
class AutoDebitRepaymentProcessorTest {

    private static final Long LOAN_ACCOUNT_ID = 1L;

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private RepayableAmountService repayableAmountService;

    @InjectMocks
    private AutoDebitRepaymentProcessor processor;

    @Test
    @DisplayName("판정 정상이고 갚을 금액이 300,000원이면 대출 1과 300,000원을 넘긴다")
    void 갚을_금액_전액을_넘긴다() {
        // given
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(LoanJudgmentStatus.NORMAL));
        givenRepayableAmount(LoanJudgmentStatus.NORMAL, "300000");

        // when
        AutoDebitTargetResponse result = processor.process(LOAN_ACCOUNT_ID);

        // then
        assertThat(result.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
        assertThat(result.getAmount()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("판정 정상이고 납입일 전이라 갚을 금액이 0원이면 넘기지 않는다")
    void 갚을_금액이_0원이면_건너뛴다() {
        // given
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(LoanJudgmentStatus.NORMAL));
        givenRepayableAmount(LoanJudgmentStatus.NORMAL, "0");

        // when
        AutoDebitTargetResponse result = processor.process(LOAN_ACCOUNT_ID);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("판정 행이 없는 새 대출이면 정상 판정으로 갚을 금액을 계산한다")
    void 판정이_없으면_정상으로_계산한다() {
        // given
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());
        givenRepayableAmount(LoanJudgmentStatus.NORMAL, "300000");

        // when
        processor.process(LOAN_ACCOUNT_ID);

        // then
        then(repayableAmountService).should().calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
    }

    private void givenRepayableAmount(LoanJudgmentStatus judgment, String totalAmount) {
        given(repayableAmountService.calculate(LOAN_ACCOUNT_ID, judgment))
                .willReturn(new RepayableAmountResponse(new BigDecimal(totalAmount), List.of()));
    }
}
