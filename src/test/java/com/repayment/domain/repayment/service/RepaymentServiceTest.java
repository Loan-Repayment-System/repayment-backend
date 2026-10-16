package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.CollectionAccountNotFoundException;
import com.repayment.domain.repayment.exception.NothingToRepayException;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import com.repayment.domain.transfer.exception.InsufficientBalanceException;
import com.repayment.domain.transfer.mapper.AccountMapper;
import com.repayment.domain.transfer.service.InternalTransferService;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.code.RepaymentTargetType;
import com.repayment.global.config.RepaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("상환 처리")
class RepaymentServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final long REPAYMENT_ACCOUNT_ID = 30L;
    private static final long COLLECTION_ACCOUNT_ID = 1L;
    private static final long REPAYMENT_ID = 500L;
    private static final String COLLECTION_ACCOUNT_NO = "100-000-000001";
    private static final String REQUEST_KEY = "req-1";
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 30);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final List<RepaymentTargetResponse> TARGETS = List.of(
            new RepaymentTargetResponse(1, RepaymentTargetType.INSTALLMENT_INTEREST, 100L, new BigDecimal("30000")));

    @Mock
    private RepaymentEligibilityService repaymentEligibilityService;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private InternalTransferService internalTransferService;

    @Mock
    private RepaymentMapper repaymentMapper;

    @Mock
    private InstallmentAllocationService installmentAllocationService;

    @Mock
    private AccelerationAllocationService accelerationAllocationService;

    @Mock
    private MaturityOverdueAllocationService maturityOverdueAllocationService;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;

    @Mock
    private DelinquencyResolutionService delinquencyResolutionService;

    @Mock
    private LoanStatusJudgmentService loanStatusJudgmentService;

    @Mock
    private LoanStatusChangeService loanStatusChangeService;

    private RepaymentService repaymentService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        repaymentService = new RepaymentService(repaymentEligibilityService, accountMapper, internalTransferService,
                repaymentMapper, installmentAllocationService, accelerationAllocationService,
                maturityOverdueAllocationService, repaymentScheduleMapper, repaymentScheduleJudgmentService,
                delinquencyResolutionService, loanStatusJudgmentService, loanStatusChangeService,
                new RepaymentProperties(COLLECTION_ACCOUNT_NO), clock);
    }

    @Nested
    @DisplayName("가장 최근 판정이 0 정상이고 판정할 회차가 2개일 때")
    class 정상_대출 {

        private final RepaymentScheduleJudgmentTargetResponse first = schedule(1L);
        private final RepaymentScheduleJudgmentTargetResponse second = schedule(2L);

        @BeforeEach
        void setUp() {
            givenEligible(LoanJudgmentStatus.NORMAL);
            givenCollectionAccount();
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of(first, second));
        }

        @Test
        @DisplayName("확인 → 이체 → 기록 → 회차 충당 → 회차 2개 판정 → 해소 → 상태 판정 → 상태 변화 처리 순서로 부른다")
        void 일곱_단계를_순서대로_부른다() {
            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            InOrder inOrder = inOrder(repaymentEligibilityService, internalTransferService, repaymentMapper,
                    installmentAllocationService, repaymentScheduleJudgmentService, delinquencyResolutionService,
                    loanStatusJudgmentService, loanStatusChangeService);
            then(repaymentEligibilityService).should(inOrder).check(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT);
            then(internalTransferService).should(inOrder)
                    .transfer(any(), any(), any(), anyString());
            then(repaymentMapper).should(inOrder).insertRepayment(any());
            then(installmentAllocationService).should(inOrder)
                    .allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(first, TODAY);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(second, TODAY);
            then(delinquencyResolutionService).should(inOrder).resolve(LOAN_ACCOUNT_ID);
            then(loanStatusJudgmentService).should(inOrder).judge(LOAN_ACCOUNT_ID, TODAY);
            then(loanStatusChangeService).should(inOrder).process(LOAN_ACCOUNT_ID, TODAY);
        }

        @Test
        @DisplayName("상환 계좌에서 수납 계좌로 30,000원을 이체하고, 같은 UUID 거래번호와 처리 시각 2026-10-16 14:30으로 고객 상환 기록을 남긴다")
        void 이체와_기록이_같은_거래번호와_시각을_쓴다() {
            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            ArgumentCaptor<String> transactionNo = ArgumentCaptor.forClass(String.class);
            then(internalTransferService).should()
                    .transfer(eq(REPAYMENT_ACCOUNT_ID), eq(COLLECTION_ACCOUNT_ID), eq(AMOUNT), transactionNo.capture());
            assertThat(UUID.fromString(transactionNo.getValue()).toString()).isEqualTo(transactionNo.getValue());

            ArgumentCaptor<RepaymentResponse> repayment = ArgumentCaptor.forClass(RepaymentResponse.class);
            then(repaymentMapper).should().insertRepayment(repayment.capture());
            RepaymentResponse recorded = repayment.getValue();
            assertThat(recorded.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
            assertThat(recorded.getRepaymentChannelId()).isEqualTo(RepaymentChannel.CUSTOMER);
            assertThat(recorded.getRequestKey()).isEqualTo(REQUEST_KEY);
            assertThat(recorded.getPaidAmount()).isEqualByComparingTo("30000");
            assertThat(recorded.getPaidAt()).isEqualTo(NOW);
            assertThat(recorded.getTransactionNo()).isEqualTo(transactionNo.getValue());
        }

        @Test
        @DisplayName("기록이 만든 상환 id 500을 돌려준다")
        void 상환_id를_돌려준다() {
            // when
            Long result = repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            assertThat(result).isEqualTo(REPAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("가장 최근 판정에 따라 충당을 고를 때")
    class 충당_고르기 {

        @BeforeEach
        void setUp() {
            givenCollectionAccount();
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, names = {"NORMAL", "BULLET_INTEREST_OVERDUE", "INSTALLMENT_OVERDUE"})
        @DisplayName("0 정상·11 이자연체·21 1회 연체면 회차 충당만 부른다")
        void 회차_방식은_회차_충당을_부른다(LoanJudgmentStatus judgment) {
            // given
            givenEligible(judgment);

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            then(installmentAllocationService).should().allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(accelerationAllocationService).shouldHaveNoInteractions();
            then(maturityOverdueAllocationService).shouldHaveNoInteractions();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, names = {"BULLET_ACCELERATION", "BULLET_ACCELERATION_CLAUSE_6",
                "INSTALLMENT_ACCELERATION", "INSTALLMENT_ACCELERATION_CLAUSE_6"})
        @DisplayName("12·13·22·23 기한이익상실이면 기한이익상실 충당만 부른다")
        void 기한이익상실은_기한이익상실_충당을_부른다(LoanJudgmentStatus judgment) {
            // given
            givenEligible(judgment);

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            then(accelerationAllocationService).should().allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(installmentAllocationService).shouldHaveNoInteractions();
            then(maturityOverdueAllocationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("1 만기 경과면 만기 경과 충당만 부른다")
        void 만기_경과는_만기_경과_충당을_부른다() {
            // given
            givenEligible(LoanJudgmentStatus.MATURITY_OVERDUE);

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            then(maturityOverdueAllocationService).should().allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(installmentAllocationService).shouldHaveNoInteractions();
            then(accelerationAllocationService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("기한이익상실로 회차가 모두 닫혀 판정할 회차가 없을 때")
    class 판정할_회차_없음 {

        @Test
        @DisplayName("스케줄 판정은 부르지 않고 해소·상태 판정·상태 변화 처리는 기준일 10/16으로 부른다")
        void 스케줄_판정만_건너뛴다() {
            // given
            givenEligible(LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            givenCollectionAccount();
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY);

            // then
            then(repaymentScheduleJudgmentService).shouldHaveNoInteractions();
            then(delinquencyResolutionService).should().resolve(LOAN_ACCOUNT_ID);
            then(loanStatusJudgmentService).should().judge(LOAN_ACCOUNT_ID, TODAY);
            then(loanStatusChangeService).should().process(LOAN_ACCOUNT_ID, TODAY);
        }
    }

    @Nested
    @DisplayName("처리 중에 실패할 때")
    class 실패 {

        @Test
        @DisplayName("상환 가능 확인이 갚을 금액 없음으로 거절하면 같은 예외를 던지고 이체부터 아무것도 부르지 않는다")
        void 확인에서_거절되면_돈이_움직이지_않는다() {
            // given
            NothingToRepayException rejected = new NothingToRepayException(LOAN_ACCOUNT_ID);
            given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT)).willThrow(rejected);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY));

            // then
            assertThat(thrown).isSameAs(rejected);
            then(accountMapper).shouldHaveNoInteractions();
            then(internalTransferService).shouldHaveNoInteractions();
            then(repaymentMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("설정한 계좌번호 100-000-000001의 수납 계좌가 없으면 CollectionAccountNotFoundException을 던지고 이체하지 않는다")
        void 수납_계좌가_없으면_이체하지_않는다() {
            // given
            givenEligible(LoanJudgmentStatus.NORMAL);
            given(accountMapper.findAccountIdByAccountNo(COLLECTION_ACCOUNT_NO)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY));

            // then
            assertThat(thrown).isInstanceOf(CollectionAccountNotFoundException.class);
            assertThat(((CollectionAccountNotFoundException) thrown).getAccountNo()).isEqualTo(COLLECTION_ACCOUNT_NO);
            then(internalTransferService).shouldHaveNoInteractions();
            then(repaymentMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이체가 잔액 부족으로 실패하면 같은 예외를 던지고 기록·충당을 부르지 않는다")
        void 이체가_실패하면_기록하지_않는다() {
            // given
            givenEligible(LoanJudgmentStatus.NORMAL);
            givenCollectionAccount();
            InsufficientBalanceException insufficient =
                    new InsufficientBalanceException(REPAYMENT_ACCOUNT_ID, new BigDecimal("10000"), AMOUNT);
            willThrow(insufficient).given(internalTransferService)
                    .transfer(any(), any(), any(), anyString());

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT, REQUEST_KEY));

            // then
            assertThat(thrown).isSameAs(insufficient);
            then(repaymentMapper).shouldHaveNoInteractions();
            then(installmentAllocationService).shouldHaveNoInteractions();
        }
    }

    private void givenEligible(LoanJudgmentStatus latestJudgment) {
        given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, MEMBER_ID, AMOUNT)).willReturn(
                new RepaymentEligibilityResponse(REPAYMENT_ACCOUNT_ID, latestJudgment,
                        new RepayableAmountResponse(AMOUNT, TARGETS)));
    }

    private void givenCollectionAccount() {
        given(accountMapper.findAccountIdByAccountNo(COLLECTION_ACCOUNT_NO)).willReturn(Optional.of(COLLECTION_ACCOUNT_ID));
    }

    // MyBatis가 INSERT 뒤 생성된 id를 DTO에 채우는 것을 흉내 낸다.
    private void givenRepaymentInserted() {
        given(repaymentMapper.insertRepayment(any())).willAnswer(invocation -> {
            RepaymentResponse repayment = invocation.getArgument(0);
            ReflectionTestUtils.setField(repayment, "repaymentId", REPAYMENT_ID);
            return 1;
        });
    }

    private static RepaymentScheduleJudgmentTargetResponse schedule(Long repaymentScheduleId) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(repaymentScheduleId)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .build();
    }
}
