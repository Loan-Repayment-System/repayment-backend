package com.repayment.batch.writer;

import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.exception.AutoDebitRepaymentAbortedException;
import com.repayment.domain.repayment.exception.InsufficientBalanceException;
import com.repayment.domain.repayment.exception.RepaymentExceedsRepayableAmountException;
import com.repayment.domain.repayment.exception.RepaymentInProgressException;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

    @Nested
    @DisplayName("Processor 계산 뒤 고객이 일부를 먼저 갚아 갚을 금액 초과로 거절될 때")
    class 갚을_금액_초과 {

        @Test
        @DisplayName("대출 1의 100,000원이 거절되고 남은 갚을 금액이 30,000원이면 30,000원으로 한 번 더 상환한다")
        void 남은_갚을_금액으로_다시_상환한다() {
            // given
            given(repaymentService.repay(1L, AMOUNT_1, RepaymentChannel.AUTO))
                    .willThrow(new RepaymentExceedsRepayableAmountException(1L, AMOUNT_1, new BigDecimal("30000")));

            // when
            writer.write(Chunk.of(new AutoDebitTargetResponse(1L, AMOUNT_1)));

            // then
            then(repaymentService).should().repay(1L, new BigDecimal("30000"), RepaymentChannel.AUTO);
        }

        @Test
        @DisplayName("30,000원으로 다시 상환해도 초과로 거절되면 대출 1은 2번에서 멈추고 대출 2를 상환한다")
        void 다시_거절되면_더_하지_않는다() {
            // given
            given(repaymentService.repay(1L, AMOUNT_1, RepaymentChannel.AUTO))
                    .willThrow(new RepaymentExceedsRepayableAmountException(1L, AMOUNT_1, new BigDecimal("30000")));
            given(repaymentService.repay(1L, new BigDecimal("30000"), RepaymentChannel.AUTO))
                    .willThrow(new RepaymentExceedsRepayableAmountException(1L, new BigDecimal("30000"),
                            new BigDecimal("10000")));

            // when
            writer.write(Chunk.of(
                    new AutoDebitTargetResponse(1L, AMOUNT_1),
                    new AutoDebitTargetResponse(2L, AMOUNT_2)));

            // then
            then(repaymentService).should(times(2)).repay(eq(1L), any(), eq(RepaymentChannel.AUTO));
            then(repaymentService).should().repay(2L, AMOUNT_2, RepaymentChannel.AUTO);
        }
    }

    @Nested
    @DisplayName("상환 모듈이 다시 시도하고도 DB 오류로 실패할 때")
    class DB_오류 {

        @Test
        @DisplayName("대출 1이 잠금 획득 실패면 그 대출만 넘어가고 대출 2를 상환한다")
        void 잠금_획득_실패는_다음_대출로_넘어간다() {
            // given
            given(repaymentService.repay(1L, AMOUNT_1, RepaymentChannel.AUTO))
                    .willThrow(new CannotAcquireLockException("NOWAIT"));

            // when
            Throwable thrown = catchThrowable(() -> writer.write(Chunk.of(
                    new AutoDebitTargetResponse(1L, AMOUNT_1),
                    new AutoDebitTargetResponse(2L, AMOUNT_2))));

            // then
            assertThat(thrown).isNull();
            then(repaymentService).should().repay(2L, AMOUNT_2, RepaymentChannel.AUTO);
        }

        @Test
        @DisplayName("대출 1이 커넥션 획득 실패면 그 오류를 원인으로 담은 중단 예외를 던지고 대출 2는 상환하지 않는다")
        void 그_밖의_DB_오류는_Job을_멈춘다() {
            // given
            CannotCreateTransactionException failure = new CannotCreateTransactionException("connection");
            given(repaymentService.repay(1L, AMOUNT_1, RepaymentChannel.AUTO)).willThrow(failure);

            // when
            Throwable thrown = catchThrowable(() -> writer.write(Chunk.of(
                    new AutoDebitTargetResponse(1L, AMOUNT_1),
                    new AutoDebitTargetResponse(2L, AMOUNT_2))));

            // then
            assertThat(thrown).isInstanceOf(AutoDebitRepaymentAbortedException.class).hasCause(failure);
            then(repaymentService).should(never()).repay(2L, AMOUNT_2, RepaymentChannel.AUTO);
        }
    }

    @Nested
    @DisplayName("같은 대출의 상환이 진행 중이라 거절될 때")
    class 상환_진행_중 {

        @Test
        @DisplayName("대출 1이 상환 진행 중이면 다음 대출로 넘어가 대출 2를 상환한다")
        void 다음_대출로_넘어간다() {
            // given
            given(repaymentService.repay(1L, AMOUNT_1, RepaymentChannel.AUTO))
                    .willThrow(new RepaymentInProgressException(1L));

            // when
            Throwable thrown = catchThrowable(() -> writer.write(Chunk.of(
                    new AutoDebitTargetResponse(1L, AMOUNT_1),
                    new AutoDebitTargetResponse(2L, AMOUNT_2))));

            // then
            assertThat(thrown).isNull();
            then(repaymentService).should().repay(2L, AMOUNT_2, RepaymentChannel.AUTO);
        }
    }
}
