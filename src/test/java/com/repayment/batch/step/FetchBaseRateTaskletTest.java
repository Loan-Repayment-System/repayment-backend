package com.repayment.batch.step;

import com.repayment.domain.baserate.exception.BaseRateFetchFailedException;
import com.repayment.domain.baserate.service.BaseRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("기준금리 조회 Tasklet")
class FetchBaseRateTaskletTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Mock
    private BaseRateService baseRateService;

    // 실제로 기다리지 않고 기다린 간격(밀리초)만 기록한다.
    private final List<Long> sleeps = new ArrayList<>();

    private FetchBaseRateTasklet tasklet;
    private StepContribution contribution;
    private ChunkContext chunkContext;

    @BeforeEach
    void setUp() {
        tasklet = new FetchBaseRateTasklet(baseRateService, sleeps::add);
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(
                new JobParametersBuilder().addLocalDate("businessDate", TODAY).toJobParameters());
        contribution = new StepContribution(stepExecution);
        chunkContext = new ChunkContext(new StepContext(stepExecution));
    }

    @Nested
    @DisplayName("첫 호출이 성공할 때")
    class 성공 {

        @Test
        @DisplayName("10/17로 1번 호출하고 기다리지 않는다")
        void 성공하면_바로_끝난다() {
            // given
            given(baseRateService.collectKoribor12m(TODAY)).willReturn(1);

            // when
            RepeatStatus status = tasklet.execute(contribution, chunkContext);

            // then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            then(baseRateService).should(times(1)).collectKoribor12m(TODAY);
            assertThat(sleeps).isEmpty();
        }
    }

    @Nested
    @DisplayName("다시 시도하는 실패(타임아웃·연결 실패, 5xx)일 때")
    class 재시도하는_실패 {

        @Test
        @DisplayName("타임아웃이 2번 난 뒤 성공하면 3번 호출하고 1초·2초 기다린다")
        void 타임아웃_뒤_성공하면_끝난다() {
            // given
            given(baseRateService.collectKoribor12m(TODAY))
                    .willThrow(new ResourceAccessException("timeout"))
                    .willThrow(new ResourceAccessException("timeout"))
                    .willReturn(1);

            // when
            RepeatStatus status = tasklet.execute(contribution, chunkContext);

            // then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            then(baseRateService).should(times(3)).collectKoribor12m(TODAY);
            assertThat(sleeps).containsExactly(1_000L, 2_000L);
        }

        @Test
        @DisplayName("5xx가 계속 나면 4번 호출하고 1초·2초·4초 기다린 뒤 HttpServerErrorException을 그대로 던진다")
        void 재시도까지_실패하면_원래_예외를_던진다() {
            // given
            HttpServerErrorException serverError = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
            given(baseRateService.collectKoribor12m(TODAY)).willThrow(serverError);

            // when & then
            assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext)).isSameAs(serverError);
            then(baseRateService).should(times(4)).collectKoribor12m(TODAY);
            assertThat(sleeps).containsExactly(1_000L, 2_000L, 4_000L);
        }
    }

    @Nested
    @DisplayName("다시 해도 안 되는 실패일 때")
    class 재시도하지_않는_실패 {

        @Test
        @DisplayName("4xx면 1번만 호출하고 HttpClientErrorException을 그대로 던진다")
        void 클라이언트_오류는_재시도하지_않는다() {
            // given
            HttpClientErrorException clientError = new HttpClientErrorException(HttpStatus.BAD_REQUEST);
            given(baseRateService.collectKoribor12m(TODAY)).willThrow(clientError);

            // when & then
            assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext)).isSameAs(clientError);
            then(baseRateService).should(times(1)).collectKoribor12m(TODAY);
            assertThat(sleeps).isEmpty();
        }

        @Test
        @DisplayName("한국은행 오류 응답이면 1번만 호출하고 BaseRateFetchFailedException을 그대로 던진다")
        void 한국은행_오류_응답은_재시도하지_않는다() {
            // given
            BaseRateFetchFailedException fetchFailed = new BaseRateFetchFailedException("한국은행 API 오류 응답 [INFO-100]");
            given(baseRateService.collectKoribor12m(TODAY)).willThrow(fetchFailed);

            // when & then
            assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext)).isSameAs(fetchFailed);
            then(baseRateService).should(times(1)).collectKoribor12m(TODAY);
            assertThat(sleeps).isEmpty();
        }

        @Test
        @DisplayName("저장이 실패하면 1번만 호출하고 DataAccessException을 그대로 던진다")
        void 저장_실패는_재시도하지_않는다() {
            // given
            DataIntegrityViolationException saveFailed = new DataIntegrityViolationException("duplicate");
            given(baseRateService.collectKoribor12m(TODAY)).willThrow(saveFailed);

            // when & then
            assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext)).isSameAs(saveFailed);
            then(baseRateService).should(times(1)).collectKoribor12m(TODAY);
            assertThat(sleeps).isEmpty();
        }
    }
}
