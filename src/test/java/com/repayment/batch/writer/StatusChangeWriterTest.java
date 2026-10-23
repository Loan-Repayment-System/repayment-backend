package com.repayment.batch.writer;

import com.repayment.domain.loan.service.LoanStatusChangeService;
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
@DisplayName("상태 변화 처리 Writer")
class StatusChangeWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Mock
    private LoanStatusChangeService loanStatusChangeService;

    @Test
    @DisplayName("chunk [3, 7]이면 대출 3과 7을 차례로 10/17로 처리한다")
    void 대출마다_기준일로_처리한다() {
        // given
        StatusChangeWriter writer = new StatusChangeWriter(loanStatusChangeService, TODAY);

        // when
        writer.write(Chunk.of(3L, 7L));

        // then
        InOrder inOrder = inOrder(loanStatusChangeService);
        then(loanStatusChangeService).should(inOrder).process(3L, TODAY);
        then(loanStatusChangeService).should(inOrder).process(7L, TODAY);
        then(loanStatusChangeService).shouldHaveNoMoreInteractions();
    }
}
