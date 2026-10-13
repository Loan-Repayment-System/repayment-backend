package com.repayment.domain.baserate.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 한국은행 ECOS StatisticSearch 응답.
 * 성공하면 StatisticSearch가, 데이터가 없거나 오류면 RESULT가 온다 (둘 다 HTTP 200).
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class BokStatisticSearchResponse {

    @JsonProperty("StatisticSearch")
    private StatisticSearch statisticSearch;

    @JsonProperty("RESULT")
    private Result result;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StatisticSearch {

        @JsonProperty("list_total_count")
        private int listTotalCount;

        @JsonProperty("row")
        private List<Row> rows;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Row {

        // 공시일 (yyyyMMdd)
        @JsonProperty("TIME")
        private String time;

        // 금리 (연%)
        @JsonProperty("DATA_VALUE")
        private String dataValue;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Result {

        @JsonProperty("CODE")
        private String code;

        @JsonProperty("MESSAGE")
        private String message;
    }
}
