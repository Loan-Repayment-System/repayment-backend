package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.service.DelinquencyResolutionService;
import com.repayment.domain.loan.service.LoanStatusChangeService;
import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.mapper.RepaymentMapper;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.LoanJudgmentStatus;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("상환 반영")
class RepaymentReflectionServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long COLLECTION_REQUEST_ID = 300L;
    private static final long REPAYMENT_ID = 500L;
    private static final BigDecimal AMOUNT = new BigDecimal("30000");
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 16, 14, 30, 15);
    private static final LocalDate SEND_DATE = LocalDate.of(2026, 10, 16);
    private static final String TRANSACTION_NO = "20261016000001";
    private static final List<RepaymentTargetResponse> TARGETS = List.of(
            new RepaymentTargetResponse(1, RepaymentTargetType.INSTALLMENT_INTEREST, 100L, new BigDecimal("30000")));
    private static final CollectionRequestResponse REQUEST = CollectionRequestResponse.builder()
            .collectionRequestId(COLLECTION_REQUEST_ID)
            .loanAccountId(LOAN_ACCOUNT_ID)
            .repaymentChannelId(RepaymentChannel.CUSTOMER)
            .collectionRequestStatusId(CollectionRequestStatus.COLLECTED)
            .sendDate(SEND_DATE)
            .telegramNo(1)
            .sentAt(SENT_AT)
            .withdrawalBankCode("999")
            .withdrawalAccountNo("200-000-000001")
            .amount(AMOUNT)
            .build();

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
    private CollectionRequestService collectionRequestService;

    @InjectMocks
    private RepaymentReflectionService repaymentReflectionService;

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
        }

        @Test
        @DisplayName("확인 → 기록 → 회차 충당 → 회차 2개 판정 → 해소 → 상태 판정 → 상태 변화 처리 → 상환 완료 표시 순서로 부른다")
        void 기록하고_반영한_뒤_상환_완료로_바꾼다() {
            // when
            repaymentReflectionService.reflect(REQUEST);

            // then
            InOrder inOrder = inOrder(repaymentEligibilityService, repaymentMapper, installmentAllocationService,
                    repaymentScheduleJudgmentService, delinquencyResolutionService, loanStatusJudgmentService,
                    loanStatusChangeService, collectionRequestService);
            then(repaymentEligibilityService).should(inOrder).check(LOAN_ACCOUNT_ID, AMOUNT, SEND_DATE);
            then(repaymentMapper).should(inOrder).insertRepayment(any());
            then(installmentAllocationService).should(inOrder)
                    .allocate(REPAYMENT_ID, LOAN_ACCOUNT_ID, AMOUNT, TARGETS);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(first, SEND_DATE);
            then(repaymentScheduleJudgmentService).should(inOrder).judge(second, SEND_DATE);
            then(delinquencyResolutionService).should(inOrder).resolve(LOAN_ACCOUNT_ID);
            then(loanStatusJudgmentService).should(inOrder).judge(LOAN_ACCOUNT_ID, SEND_DATE);
            then(loanStatusChangeService).should(inOrder).process(LOAN_ACCOUNT_ID, SEND_DATE);
            then(collectionRequestService).should(inOrder).markCompleted(COLLECTION_REQUEST_ID);
        }

        @Test
        @DisplayName("10/16 전문번호 1로 보낸 집금이면 30,000원을 거래번호 20261016000001, UUID 요청 키, 요청의 채널(고객 상환)로 기록한다")
        void 보낸_키를_거래번호로_기록한다() {
            // when
            repaymentReflectionService.reflect(REQUEST);

            // then
            RepaymentResponse recorded = captureRepayment();
            assertThat(recorded.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
            assertThat(recorded.getRepaymentChannelId()).isEqualTo(RepaymentChannel.CUSTOMER);
            assertThat(UUID.fromString(recorded.getRequestKey()).toString()).isEqualTo(recorded.getRequestKey());
            assertThat(recorded.getTransactionNo()).isEqualTo(TRANSACTION_NO);
            assertThat(recorded.getPaidAmount()).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("집금을 2026-10-16 14:30:15에 보냈으면 납부 시각은 14:30:15, 확인·판정 기준일은 10/16이다")
        void 보낸_시각으로_기록하고_그_날짜로_판정한다() {
            // when
            repaymentReflectionService.reflect(REQUEST);

            // then
            assertThat(captureRepayment().getPaidAt()).isEqualTo(SENT_AT);
            then(repaymentEligibilityService).should().check(LOAN_ACCOUNT_ID, AMOUNT, SEND_DATE);
            then(loanStatusJudgmentService).should().judge(LOAN_ACCOUNT_ID, SEND_DATE);
        }
    }

    @Nested
    @DisplayName("가장 최근 판정에 따라 충당을 고를 때")
    class 충당_고르기 {

        @BeforeEach
        void setUp() {
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
            repaymentReflectionService.reflect(REQUEST);

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
            repaymentReflectionService.reflect(REQUEST);

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
            repaymentReflectionService.reflect(REQUEST);

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

            // when
            repaymentReflectionService.reflect(REQUEST);

            // then
            then(repaymentScheduleJudgmentService).shouldHaveNoInteractions();
            then(delinquencyResolutionService).should().resolve(LOAN_ACCOUNT_ID);
            then(loanStatusJudgmentService).should().judge(LOAN_ACCOUNT_ID, SEND_DATE);
            then(loanStatusChangeService).should().process(LOAN_ACCOUNT_ID, SEND_DATE);
        }
    }

    private void givenEligible(LoanJudgmentStatus latestJudgment) {
        given(repaymentEligibilityService.check(LOAN_ACCOUNT_ID, AMOUNT, SEND_DATE)).willReturn(
                new RepaymentEligibilityResponse("999", "200-000-000001", latestJudgment,
                        new RepayableAmountResponse(AMOUNT, TARGETS)));
    }

    // MyBatis가 INSERT 뒤 생성된 id를 DTO에 채우는 것을 흉내 낸다.
    private void givenRepaymentInserted() {
        given(repaymentMapper.insertRepayment(any())).willAnswer(invocation -> {
            RepaymentResponse repayment = invocation.getArgument(0);
            ReflectionTestUtils.setField(repayment, "repaymentId", REPAYMENT_ID);
            return 1;
        });
    }

    private RepaymentResponse captureRepayment() {
        ArgumentCaptor<RepaymentResponse> repayment = ArgumentCaptor.forClass(RepaymentResponse.class);
        then(repaymentMapper).should().insertRepayment(repayment.capture());
        return repayment.getValue();
    }

    private static RepaymentScheduleJudgmentTargetResponse schedule(Long repaymentScheduleId) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(repaymentScheduleId)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .build();
    }
}
