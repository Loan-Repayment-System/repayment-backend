package com.repayment.domain.repayment.service;

import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.exception.RepaymentInProgressException;
import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("집금 요청 저장")
class CollectionRequestServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 17, 14, 30, 15);
    private static final LocalDate SEND_DATE = LocalDate.of(2026, 10, 17);

    @Mock
    private RepaymentEligibilityService repaymentEligibilityService;

    @Mock
    private TelegramNoService telegramNoService;

    @Mock
    private CollectionRequestMapper collectionRequestMapper;

    @InjectMocks
    private CollectionRequestService collectionRequestService;

    @Nested
    @DisplayName("상환 가능 확인을 통과할 때")
    class 상환_가능_확인_통과 {

        @Test
        @DisplayName("처리 중인 상환이 없으면 요청 상태 · 10/17 · 전문번호 7 · 999 / 200-000-000001 · 30,000원으로 1건 저장하고 돌려준다")
        void 처리_중인_상환이_없으면_집금_요청을_저장한다() {
            // given
            givenEligible();
            given(collectionRequestMapper.existsInProgressByLoanAccountId(LOAN_ACCOUNT_ID)).willReturn(false);
            given(telegramNoService.next(SEND_DATE)).willReturn(7);

            // when
            CollectionRequestResponse result = create();

            // then
            ArgumentCaptor<CollectionRequestResponse> captor = ArgumentCaptor.forClass(CollectionRequestResponse.class);
            then(collectionRequestMapper).should().insertCollectionRequest(captor.capture());
            CollectionRequestResponse saved = captor.getValue();
            assertThat(result).isSameAs(saved);
            assertThat(saved.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
            assertThat(saved.getRepaymentChannelId()).isEqualTo(RepaymentChannel.CUSTOMER);
            assertThat(saved.getCollectionRequestStatusId()).isEqualTo(CollectionRequestStatus.REQUESTED);
            assertThat(saved.getSendDate()).isEqualTo(SEND_DATE);
            assertThat(saved.getTelegramNo()).isEqualTo(7);
            assertThat(saved.getSentAt()).isEqualTo(SENT_AT);
            assertThat(saved.getWithdrawalBankCode()).isEqualTo("999");
            assertThat(saved.getWithdrawalAccountNo()).isEqualTo("200-000-000001");
            assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
        }

        @Test
        @DisplayName("처리 중인 상환이 있으면 처리 중인 상환 있음 예외를 던지고, 채번하지 않고 저장하지 않는다")
        void 처리_중인_상환이_있으면_거절한다() {
            // given
            givenEligible();
            given(collectionRequestMapper.existsInProgressByLoanAccountId(LOAN_ACCOUNT_ID)).willReturn(true);

            // when
            Throwable thrown = catchThrowable(() -> create());

            // then
            assertThat(thrown).isInstanceOf(RepaymentInProgressException.class);
            then(telegramNoService).shouldHaveNoInteractions();
            then(collectionRequestMapper).should(never()).insertCollectionRequest(any());
        }
    }

    @Nested
    @DisplayName("상환 가능 확인에서 거절될 때")
    class 상환_가능_확인_거절 {

        @Test
        @DisplayName("완제 예외를 그대로 던지고, 처리 중인 상환 확인 · 채번 · 저장을 하지 않는다")
        void 확인_예외를_그대로_던진다() {
            // given
            given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, AMOUNT, SEND_DATE))
                    .willThrow(new LoanAlreadyPaidOffException(LOAN_ACCOUNT_ID));

            // when
            Throwable thrown = catchThrowable(() -> create());

            // then
            assertThat(thrown).isInstanceOf(LoanAlreadyPaidOffException.class);
            then(collectionRequestMapper).shouldHaveNoInteractions();
            then(telegramNoService).shouldHaveNoInteractions();
        }
    }

    private void givenEligible() {
        given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, AMOUNT, SEND_DATE)).willReturn(
                new RepaymentEligibilityResponse("999", "200-000-000001", LoanJudgmentStatus.NORMAL,
                        new RepayableAmountResponse(AMOUNT, List.of())));
    }

    private CollectionRequestResponse create() {
        return collectionRequestService.create(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER, SENT_AT);
    }
}
