package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.dto.response.LoanAccountStatusResponse;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.NothingToRepayException;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.code.RepaymentTargetType;
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
    private static final long REPAYMENT_ID = 500L;
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 30, 15, 789_000_000);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 16, 14, 30, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final List<RepaymentTargetResponse> TARGETS = List.of(
            new RepaymentTargetResponse(1, RepaymentTargetType.INSTALLMENT_INTEREST, 100L, new BigDecimal("30000")));

    @Mock
    private RepaymentEligibilityService repaymentEligibilityService;

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

    @Mock
    private LoanAccountMapper loanAccountMapper;

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private RepayableAmountService repayableAmountService;

    private RepaymentService repaymentService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        repaymentService = new RepaymentService(repaymentEligibilityService, repaymentMapper,
                installmentAllocationService, accelerationAllocationService, maturityOverdueAllocationService, repaymentScheduleMapper, repaymentScheduleJudgmentService,
                delinquencyResolutionService, loanStatusJudgmentService, loanStatusChangeService,
                loanAccountMapper, loanStatusJudgmentMapper, repayableAmountService, clock);
    }

    @Nested
    @DisplayName("가장 최근 판정이 0 정상이고 판정할 회차가 2개일 때")
    class 정상_대출 {

        private final RepaymentScheduleJudgmentTargetResponse first = schedule(1L);
        private final RepaymentScheduleJudgmentTargetResponse second = schedule(2L);

        @BeforeEach
        void setUp() {
            givenEligible(LoanJudgmentStatus.NORMAL);
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of(first, second));
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "10000");
        }

        @Test
        @DisplayName("확인 → 기록 → 회차 충당 → 회차 2개 판정 → 해소 → 상태 판정 → 상태 변화 처리 → 결과 조회 순서로 부른다")
        void 일곱_단계_뒤에_결과를_조회한다() {
            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            InOrder inOrder = inOrder(repaymentEligibilityService, repaymentMapper,
                    installmentAllocationService, repaymentScheduleJudgmentService, delinquencyResolutionService,
                    loanStatusJudgmentService, loanStatusChangeService, loanAccountMapper, repayableAmountService);
            then(repaymentEligibilityService).should(inOrder).check(LOAN_ACCOUNT_ID, AMOUNT, TODAY);
            then(repaymentMapper).should(inOrder).insertRepayment(any());
            then(installmentAllocationService).should(inOrder)
                    .allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(first, TODAY);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(second, TODAY);
            then(delinquencyResolutionService).should(inOrder).resolve(LOAN_ACCOUNT_ID);
            then(loanStatusJudgmentService).should(inOrder).judge(LOAN_ACCOUNT_ID, TODAY);
            then(loanStatusChangeService).should(inOrder).process(LOAN_ACCOUNT_ID, TODAY);
            then(loanAccountMapper).should(inOrder).findLoanAccountStatus(LOAN_ACCOUNT_ID);
            then(repayableAmountService).should(inOrder).calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
        }

        @Test
        @DisplayName("30,000원을 UUID 거래번호와 그와 다른 UUID 요청 키, 넘긴 채널(고객 상환)로 기록한다")
        void 거래번호와_요청_키를_새로_만들어_기록한다() {
            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            ArgumentCaptor<RepaymentResponse> repayment = ArgumentCaptor.forClass(RepaymentResponse.class);
            then(repaymentMapper).should().insertRepayment(repayment.capture());
            RepaymentResponse recorded = repayment.getValue();
            assertThat(recorded.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
            assertThat(recorded.getRepaymentChannelId()).isEqualTo(RepaymentChannel.CUSTOMER);
            assertThat(UUID.fromString(recorded.getRequestKey()).toString()).isEqualTo(recorded.getRequestKey());
            assertThat(UUID.fromString(recorded.getTransactionNo()).toString()).isEqualTo(recorded.getTransactionNo());
            assertThat(recorded.getRequestKey()).isNotEqualTo(recorded.getTransactionNo());
            assertThat(recorded.getPaidAmount()).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("상환 시각이 14:30:15.789면 기록과 응답의 납부 시각은 초 미만을 버린 14:30:15다")
        void 납부_시각은_초_미만을_버린다() {
            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            ArgumentCaptor<RepaymentResponse> repayment = ArgumentCaptor.forClass(RepaymentResponse.class);
            then(repaymentMapper).should().insertRepayment(repayment.capture());
            assertThat(repayment.getValue().getPaidAt()).isEqualTo(PAID_AT);
            assertThat(result.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("상환 시각 2026-10-16 14:30:15의 날짜 10/16을 기준일로 상환 가능 확인에 넘긴다")
        void 상환_시각의_날짜로_상환_가능_확인을_부른다() {
            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            then(repaymentEligibilityService).should().check(LOAN_ACCOUNT_ID, AMOUNT, TODAY);
        }

        @Test
        @DisplayName("상환 뒤 원금잔액 9,000,000원·정상·갚을 금액 10,000원이면 30,000원, 14:30:15, 9,000,000원, NORMAL, 10,000원을 돌려준다")
        void 상환_뒤_상태로_결과를_채운다() {
            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            assertThat(result.getPaidAmount()).isEqualByComparingTo("30000");
            assertThat(result.getPaidAt()).isEqualTo(PAID_AT);
            assertThat(result.getLoanPrincipalBalance()).isEqualByComparingTo("9000000");
            assertThat(result.getLoanStatus()).isEqualTo(LoanStatus.NORMAL);
            assertThat(result.getRemainingRepayableAmount()).isEqualByComparingTo("10000");
        }
    }

    @Nested
    @DisplayName("이번 상환으로 완제될 때")
    class 완제 {

        @Test
        @DisplayName("상환 뒤 판정이 2 완제면 완제 판정으로 갚을 금액을 계산해 남은 갚을 금액 0원, 원금잔액 0원, PAID_OFF를 돌려준다")
        void 상환_뒤_판정으로_남은_금액을_계산한다() {
            // given
            givenEligible(LoanJudgmentStatus.NORMAL);
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());
            givenResult("0", LoanStatus.PAID_OFF, LoanJudgmentStatus.PAID_OFF, "0");

            // when
            RepaymentResultResponse result = repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

            // then
            then(repayableAmountService).should().calculate(LOAN_ACCOUNT_ID, LoanJudgmentStatus.PAID_OFF);
            assertThat(result.getRemainingRepayableAmount()).isEqualByComparingTo("0");
            assertThat(result.getLoanPrincipalBalance()).isEqualByComparingTo("0");
            assertThat(result.getLoanStatus()).isEqualTo(LoanStatus.PAID_OFF);
        }
    }

    @Nested
    @DisplayName("가장 최근 판정에 따라 충당을 고를 때")
    class 충당_고르기 {

        @BeforeEach
        void setUp() {
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());
            givenResult("9000000", LoanStatus.NORMAL, LoanJudgmentStatus.NORMAL, "0");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, names = {"NORMAL", "BULLET_INTEREST_OVERDUE", "INSTALLMENT_OVERDUE"})
        @DisplayName("0 정상·11 이자연체·21 1회 연체면 회차 충당만 부른다")
        void 회차_방식은_회차_충당을_부른다(LoanJudgmentStatus judgment) {
            // given
            givenEligible(judgment);

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

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
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

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
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

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
            givenRepaymentInserted();
            given(repaymentScheduleMapper.findJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());
            givenResult("9000000", LoanStatus.ACCELERATED, LoanJudgmentStatus.INSTALLMENT_ACCELERATION, "500000");

            // when
            repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER);

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
        @DisplayName("상환 가능 확인이 갚을 금액 없음으로 거절하면 같은 예외를 던지고 기록부터 아무것도 부르지 않는다")
        void 확인에서_거절되면_기록하지_않는다() {
            // given
            NothingToRepayException rejected = new NothingToRepayException(LOAN_ACCOUNT_ID);
            given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, AMOUNT, TODAY)).willThrow(rejected);

            // when
            Throwable thrown = catchThrowable(() -> repaymentService.repay(LOAN_ACCOUNT_ID, AMOUNT, RepaymentChannel.CUSTOMER));

            // then
            assertThat(thrown).isSameAs(rejected);
            then(repaymentMapper).shouldHaveNoInteractions();
        }
    }

    private void givenEligible(LoanJudgmentStatus latestJudgment) {
        given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, AMOUNT, TODAY)).willReturn(
                new RepaymentEligibilityResponse("999", "110-123-456789", latestJudgment,
                        new RepayableAmountResponse(AMOUNT, TARGETS)));
    }

    // 상환을 마친 뒤 읽는 원금잔액·대출 상태와, 상환 뒤 판정으로 계산한 갚을 금액
    private void givenResult(String principalBalance, LoanStatus loanStatus, LoanJudgmentStatus latestJudgment,
                             String repayableAmount) {
        given(loanAccountMapper.findLoanAccountStatus(LOAN_ACCOUNT_ID))
                .willReturn(Optional.of(new LoanAccountStatusResponse(new BigDecimal(principalBalance), loanStatus)));
        given(loanStatusJudgmentMapper.findLatestJudgmentStatus(LOAN_ACCOUNT_ID)).willReturn(Optional.of(latestJudgment));
        given(repayableAmountService.calculate(LOAN_ACCOUNT_ID, latestJudgment))
                .willReturn(new RepayableAmountResponse(new BigDecimal(repayableAmount), List.of()));
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
