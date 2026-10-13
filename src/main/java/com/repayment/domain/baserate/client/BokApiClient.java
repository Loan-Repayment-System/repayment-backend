package com.repayment.domain.baserate.client;

import com.repayment.domain.baserate.dto.response.BaseRateResponse;
import com.repayment.domain.baserate.exception.BaseRateFetchFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 한국은행 ECOS API에서 KORIBOR(12개월) 금리를 기간으로 조회한다.
 *
 * 타임아웃·연결 실패(ResourceAccessException)와 4xx·5xx(RestClientResponseException)는 RestClient 예외를 그대로 던지고,
 * ECOS 오류 응답과 응답 형식 오류는 BaseRateFetchFailedException을 던진다.
 */
@Component
public class BokApiClient {

    // 통계표 817Y002 시장금리(일별), 항목 010152000 KORIBOR(12개월)
    private static final String STAT_CODE = "817Y002";
    private static final String KORIBOR_12M_ITEM_CODE = "010152000";
    private static final String NO_DATA_RESULT_CODE = "INFO-200";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String authKey;

    public BokApiClient(RestClient.Builder restClientBuilder,
                        @Value("${bok.api.base-url}") String baseUrl,
                        @Value("${bok.api.auth-key}") String authKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.authKey = authKey;
    }

    /**
     * startDate~endDate(둘 다 포함)에 공시된 금리를 공시일 순으로 돌려준다. 공시가 없으면 빈 목록이다.
     */
    public List<BaseRateResponse> fetchKoribor12m(LocalDate startDate, LocalDate endDate) {
        // 하루에 최대 1건이므로 기간의 일수만큼 받으면 빠짐없이 온다.
        long maxRows = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        BokStatisticSearchResponse response = restClient.get()
                .uri("/StatisticSearch/{authKey}/json/kr/1/{maxRows}/{statCode}/D/{start}/{end}/{itemCode}",
                        authKey, maxRows, STAT_CODE,
                        startDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                        endDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                        KORIBOR_12M_ITEM_CODE)
                .retrieve()
                .body(BokStatisticSearchResponse.class);

        if (response == null) {
            throw new BaseRateFetchFailedException("응답 본문이 비어 있습니다.");
        }
        if (response.getResult() != null) {
            return handleResult(response.getResult());
        }
        if (response.getStatisticSearch() == null || response.getStatisticSearch().getRows() == null) {
            throw new BaseRateFetchFailedException("응답에 StatisticSearch.row가 없습니다.");
        }
        return response.getStatisticSearch().getRows().stream()
                .map(this::toBaseRate)
                .toList();
    }

    // 데이터 없음(INFO-200)은 빈 목록, 그 외 코드(인증키 오류 등)는 실패로 본다.
    private List<BaseRateResponse> handleResult(BokStatisticSearchResponse.Result result) {
        if (NO_DATA_RESULT_CODE.equals(result.getCode())) {
            return List.of();
        }
        throw new BaseRateFetchFailedException(
                "한국은행 API 오류 응답 [" + result.getCode() + "] " + result.getMessage());
    }

    private BaseRateResponse toBaseRate(BokStatisticSearchResponse.Row row) {
        if (row.getTime() == null || row.getDataValue() == null) {
            throw new BaseRateFetchFailedException(
                    "응답 행에 값이 없습니다. TIME=" + row.getTime() + ", DATA_VALUE=" + row.getDataValue());
        }
        try {
            return BaseRateResponse.builder()
                    .announcedDate(LocalDate.parse(row.getTime(), DateTimeFormatter.BASIC_ISO_DATE))
                    .rate(new BigDecimal(row.getDataValue()))
                    .build();
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new BaseRateFetchFailedException(
                    "응답 행을 해석할 수 없습니다. TIME=" + row.getTime() + ", DATA_VALUE=" + row.getDataValue(), e);
        }
    }
}
