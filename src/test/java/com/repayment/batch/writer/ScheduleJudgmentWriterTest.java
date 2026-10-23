package com.repayment.batch.writer;

import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.time.LocalDate;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 스케줄 판정 Writer")
class ScheduleJudgmentWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Mock
    private RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;

    @Test
    @DisplayName("chunk [3, 7]이면 대출 3과 7을 차례로 10/17로 판정한다")
    void 대출마다_기준일로_판정한다() {
        // given
        ScheduleJudgmentWriter writer = new ScheduleJudgmentWriter(repaymentScheduleJudgmentService, TODAY);

        // when
        writer.write(Chunk.of(3L, 7L));

        // then
        InOrder inOrder = inOrder(repaymentScheduleJudgmentService);
        then(repaymentScheduleJudgmentService).should(inOrder).judgeLoan(3L, TODAY);
        then(repaymentScheduleJudgmentService).should(inOrder).judgeLoan(7L, TODAY);
        then(repaymentScheduleJudgmentService).shouldHaveNoMoreInteractions();
    }
}
