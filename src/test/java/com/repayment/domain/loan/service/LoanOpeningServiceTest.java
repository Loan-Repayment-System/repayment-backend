package com.repayment.domain.loan.service;

import com.repayment.domain.baserate.exception.BaseRateNotFoundException;
import com.repayment.domain.baserate.mapper.BaseRateHistoryMapper;
import com.repayment.domain.loan.dto.response.LoanAccountResponse;
import com.repayment.domain.loan.dto.response.LoanContractResponse;
import com.repayment.domain.loan.exception.InvalidContractPrincipalException;
import com.repayment.domain.loan.exception.InvalidTotalInstallmentsException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanContractMapper;
import com.repayment.domain.product.exception.LoanProductNotFoundException;
import com.repayment.domain.product.mapper.LoanProductMapper;
import com.repayment.domain.schedule.dto.response.ScheduledInstallmentResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.LoanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 개설")
class LoanOpeningServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final long PRODUCT_ID = 2L;
    private static final String REPAYMENT_BANK_CODE = "999";
    private static final String REPAYMENT_ACCOUNT_NO = "110-123-456789";
    private static final long CONTRACT_ID = 123L;
    private static final long LOAN_ACCOUNT_ID = 456L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final BigDecimal PRINCIPAL = new BigDecimal("12000000");

    @Mock
    private LoanProductMapper loanProductMapper;

    @Mock
    private BaseRateHistoryMapper baseRateHistoryMapper;

    @Mock
    private LoanContractMapper loanContractMapper;

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Nested
    @DisplayName("개설할 수 없는 요청을 검사할 때")
    class 검사 {

        @Test
        @DisplayName("상품이 없으면 LoanProductNotFoundException을 던지고 아무것도 만들지 않는다")
        void 없는_상품은_거절한다() {
            // given
            given(loanProductMapper.findAdditionalRateById(PRODUCT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12))
                    .isInstanceOf(LoanProductNotFoundException.class);
            thenNothingCreated();
        }

        @Test
        @DisplayName("약정금액이 0원이면 InvalidContractPrincipalException을 던진다")
        void 영원은_거절한다() {
            // given
            givenProduct();

            // when & then
            assertThatThrownBy(() -> serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, BigDecimal.ZERO, 12))
                    .isInstanceOf(InvalidContractPrincipalException.class);
        }

        @Test
        @DisplayName("약정금액이 1,000.5원이면 원 단위가 아니라 InvalidContractPrincipalException을 던진다")
        void 원_미만_자릿수는_거절한다() {
            // given
            givenProduct();

            // when & then
            assertThatThrownBy(() -> serviceAt(TODAY)
                    .open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, new BigDecimal("1000.5"), 12))
                    .isInstanceOf(InvalidContractPrincipalException.class);
        }

        @Test
        @DisplayName("회차 수가 0이면 InvalidTotalInstallmentsException을 던진다")
        void 회차_수_0은_거절한다() {
            // given
            givenProduct();

            // when & then
            assertThatThrownBy(() -> serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 0))
                    .isInstanceOf(InvalidTotalInstallmentsException.class);
        }

        @Test
        @DisplayName("계약일 10/17보다 앞서 공시된 기준금리가 없으면 BaseRateNotFoundException을 던지고 아무것도 만들지 않는다")
        void 기준금리가_없으면_거절한다() {
            // given
            givenProduct();
            given(baseRateHistoryMapper.findLatestRateBefore(TODAY)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12))
                    .isInstanceOf(BaseRateNotFoundException.class);
            thenNothingCreated();
        }
    }

    @Nested
    @DisplayName("10/17에 가산금리 1.5% 상품으로 12,000,000원 12회를 개설하고 앞서 공시된 금리가 3.2%일 때")
    class 개설 {

        @BeforeEach
        void setUp() {
            givenOpenable(TODAY);
        }

        @Test
        @DisplayName("계약은 계약일 10/17, 만기일 2027-10-17, 약정 12,000,000원, 기준 3.2%, 가산 1.5%, 12회다")
        void 계약을_만든다() {
            // when
            serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12);

            // then
            LoanContractResponse contract = captureContract();
            assertThat(contract.getLoanProductId()).isEqualTo(PRODUCT_ID);
            assertThat(contract.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(contract.getContractDate()).isEqualTo(TODAY);
            assertThat(contract.getMaturityDate()).isEqualTo(LocalDate.of(2027, 10, 17));
            assertThat(contract.getContractPrincipal()).isEqualByComparingTo("12000000");
            assertThat(contract.getContractBaseRate()).isEqualByComparingTo("3.2");
            assertThat(contract.getContractAdditionalRate()).isEqualByComparingTo("1.5");
            assertThat(contract.getTotalInstallments()).isEqualTo(12);
        }

        @Test
        @DisplayName("계약 id 123이면 대출 계좌는 900-000-000123, 상환 계좌 999 110-123-456789, 정상, 원금잔액 12,000,000원, 납입일 17이다")
        void 대출_계좌를_만든다() {
            // when
            serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12);

            // then
            ArgumentCaptor<LoanAccountResponse> captor = ArgumentCaptor.forClass(LoanAccountResponse.class);
            then(loanAccountMapper).should().insertLoanAccount(captor.capture());
            LoanAccountResponse loanAccount = captor.getValue();
            assertThat(loanAccount.getLoanContractId()).isEqualTo(CONTRACT_ID);
            assertThat(loanAccount.getAccountNo()).isEqualTo("900-000-000123");
            assertThat(loanAccount.getRepaymentBankCode()).isEqualTo(REPAYMENT_BANK_CODE);
            assertThat(loanAccount.getRepaymentAccountNo()).isEqualTo(REPAYMENT_ACCOUNT_NO);
            assertThat(loanAccount.getLoanStatusId()).isEqualTo(LoanStatus.NORMAL);
            assertThat(loanAccount.getLoanPrincipalBalance()).isEqualByComparingTo("12000000");
            assertThat(loanAccount.getMonthlyPaymentDay()).isEqualTo(17);
        }

        @Test
        @DisplayName("회차는 1회 2026-11-17부터 12회 2027-10-17까지 매월 17일로 12개를 대출 계좌 456에 만든다")
        void 회차를_만든다() {
            // when
            serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12);

            // then
            List<ScheduledInstallmentResponse> installments = captureInstallments();
            assertThat(installments).hasSize(12);
            assertThat(installments.get(0).getInstallmentNo()).isEqualTo(1);
            assertThat(installments.get(0).getScheduledDate()).isEqualTo(LocalDate.of(2026, 11, 17));
            assertThat(installments.get(1).getScheduledDate()).isEqualTo(LocalDate.of(2026, 12, 17));
            assertThat(installments.get(11).getInstallmentNo()).isEqualTo(12);
            assertThat(installments.get(11).getScheduledDate()).isEqualTo(LocalDate.of(2027, 10, 17));
        }

        @Test
        @DisplayName("만든 대출 계좌 id 456을 돌려준다")
        void 대출_계좌_id를_돌려준다() {
            // when
            Long result = serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 12);

            // then
            assertThat(result).isEqualTo(LOAN_ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("예정일을 정할 때")
    class 예정일 {

        @Test
        @DisplayName("1/31에 3회로 개설하면 예정일은 2/28·3/31·4/30, 납입일 31, 만기일 4/30이다")
        void 말일_계약은_짧은_달의_말일로_당긴다() {
            // given
            LocalDate contractDate = LocalDate.of(2027, 1, 31);
            givenOpenable(contractDate);

            // when
            serviceAt(contractDate).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 3);

            // then
            assertThat(captureInstallments())
                    .extracting(ScheduledInstallmentResponse::getInstallmentNo, ScheduledInstallmentResponse::getScheduledDate)
                    .containsExactly(
                            tuple(1, LocalDate.of(2027, 2, 28)),
                            tuple(2, LocalDate.of(2027, 3, 31)),
                            tuple(3, LocalDate.of(2027, 4, 30)));
            assertThat(captureContract().getMaturityDate()).isEqualTo(LocalDate.of(2027, 4, 30));
            ArgumentCaptor<LoanAccountResponse> captor = ArgumentCaptor.forClass(LoanAccountResponse.class);
            then(loanAccountMapper).should().insertLoanAccount(captor.capture());
            assertThat(captor.getValue().getMonthlyPaymentDay()).isEqualTo(31);
        }

        @Test
        @DisplayName("윤년 2028-01-31에 1회로 개설하면 예정일과 만기일은 2028-02-29다")
        void 윤년_2월은_29일이다() {
            // given
            LocalDate contractDate = LocalDate.of(2028, 1, 31);
            givenOpenable(contractDate);

            // when
            serviceAt(contractDate).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 1);

            // then
            assertThat(captureInstallments())
                    .extracting(ScheduledInstallmentResponse::getScheduledDate)
                    .containsExactly(LocalDate.of(2028, 2, 29));
            assertThat(captureContract().getMaturityDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("10/17에 1회로 개설하면 회차는 1개이고 만기일은 1회 예정일 2026-11-17과 같다")
        void 회차가_1개면_만기일이_1회_예정일이다() {
            // given
            givenOpenable(TODAY);

            // when
            serviceAt(TODAY).open(MEMBER_ID, PRODUCT_ID, REPAYMENT_BANK_CODE, REPAYMENT_ACCOUNT_NO, PRINCIPAL, 1);

            // then
            List<ScheduledInstallmentResponse> installments = captureInstallments();
            assertThat(installments).hasSize(1);
            assertThat(installments.get(0).getScheduledDate()).isEqualTo(LocalDate.of(2026, 11, 17));
            assertThat(captureContract().getMaturityDate()).isEqualTo(LocalDate.of(2026, 11, 17));
        }
    }

    private LoanOpeningService serviceAt(LocalDate date) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(date.atTime(10, 0).atZone(zone).toInstant(), zone);
        return new LoanOpeningService(loanProductMapper, baseRateHistoryMapper, loanContractMapper,
                loanAccountMapper, repaymentScheduleMapper, clock);
    }

    private void givenProduct() {
        given(loanProductMapper.findAdditionalRateById(PRODUCT_ID)).willReturn(Optional.of(new BigDecimal("1.5")));
    }

    private void givenBaseRate(LocalDate contractDate) {
        given(baseRateHistoryMapper.findLatestRateBefore(contractDate)).willReturn(Optional.of(new BigDecimal("3.2")));
    }

    // 모든 검사를 통과하고, INSERT하면 DB처럼 계약 id 123·대출 계좌 id 456을 채운다
    private void givenOpenable(LocalDate contractDate) {
        givenProduct();
        givenBaseRate(contractDate);
        willAnswer(invocation -> {
            ReflectionTestUtils.setField((Object) invocation.getArgument(0), "loanContractId", CONTRACT_ID);
            return 1;
        }).given(loanContractMapper).insertLoanContract(any());
        willAnswer(invocation -> {
            ReflectionTestUtils.setField((Object) invocation.getArgument(0), "loanAccountId", LOAN_ACCOUNT_ID);
            return 1;
        }).given(loanAccountMapper).insertLoanAccount(any());
    }

    private LoanContractResponse captureContract() {
        ArgumentCaptor<LoanContractResponse> captor = ArgumentCaptor.forClass(LoanContractResponse.class);
        then(loanContractMapper).should().insertLoanContract(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ScheduledInstallmentResponse> captureInstallments() {
        ArgumentCaptor<List<ScheduledInstallmentResponse>> captor = ArgumentCaptor.forClass(List.class);
        then(repaymentScheduleMapper).should().insertScheduledInstallments(eq(LOAN_ACCOUNT_ID), captor.capture());
        return captor.getValue();
    }

    private void thenNothingCreated() {
        then(loanContractMapper).shouldHaveNoInteractions();
        then(loanAccountMapper).shouldHaveNoInteractions();
        then(repaymentScheduleMapper).should(never()).insertScheduledInstallments(anyLong(), anyList());
    }
}
