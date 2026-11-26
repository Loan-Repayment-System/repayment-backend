package com.repayment.batch.writer;

import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("자동 이체 상환 Writer")
class AutoDebitRepaymentWriterTest {

    private static final BigDecimal AMOUNT_1 = new BigDecimal("100000");
    private static final BigDecimal AMOUNT_2 = new BigDecimal("200000");
    private static final BigDecimal AMOUNT_3 = new BigDecimal("300000");

    @Mock
    private RepaymentService repaymentService;

    @InjectMocks
    private AutoDebitRepaymentWriter writer;

    @Test
    @DisplayName("대출 1, 2를 받으면 차례로 각 갚을 금액을 자동 상환으로 상환한다")
    void 대출마다_자동_상환으로_상환한다() {
        // given
        Chunk<AutoDebitTargetResponse> targets = Chunk.of(
                new AutoDebitTargetResponse(1L, AMOUNT_1),
                new AutoDebitTargetResponse(2L, AMOUNT_2));

        // when
        writer.write(targets);

        // then
        InOrder inOrder = inOrder(repaymentService);
        then(repaymentService).should(inOrder).repay(1L, AMOUNT_1, RepaymentChannel.AUTO);
        then(repaymentService).should(inOrder).repay(2L, AMOUNT_2, RepaymentChannel.AUTO);
        then(repaymentService).shouldHaveNoMoreInteractions();
    }

    // 요구사항 9: 한 대출이 실패해도 나머지 대출은 계속 처리한다
    @Test
    @DisplayName("대출 1, 2, 3 중 2가 잔액 부족으로 거절되어도 3까지 상환하고 예외를 던지지 않는다")
    void 한_대출이_실패해도_다음_대출을_상환한다() {
        // given
        given(repaymentService.repay(2L, AMOUNT_2, RepaymentChannel.AUTO))
                .willThrow(new InsufficientBalanceException(LocalDate.of(2026, 10, 17), 1));
        Chunk<AutoDebitTargetResponse> targets = Chunk.of(
                new AutoDebitTargetResponse(1L, AMOUNT_1),
                new AutoDebitTargetResponse(2L, AMOUNT_2),
                new AutoDebitTargetResponse(3L, AMOUNT_3));

        // when
        Throwable thrown = catchThrowable(() -> writer.write(targets));

        // then
        assertThat(thrown).isNull();
        then(repaymentService).should().repay(3L, AMOUNT_3, RepaymentChannel.AUTO);
    }
}
