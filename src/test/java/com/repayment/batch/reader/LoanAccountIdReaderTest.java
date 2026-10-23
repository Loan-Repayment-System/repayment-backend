package com.repayment.batch.reader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대출 ID keyset 페이징 Reader")
class LoanAccountIdReaderTest {

    @Test
    @DisplayName("대상 ID 1~5를 페이지 크기 2로 읽으면 1~5 다음 null이고, 덜 찬 세 번째 페이지에서 멈춰 3번 조회한다")
    void 페이지를_이어_읽고_덜_찬_페이지에서_멈춘다() {
        // given
        FakePageQuery query = new FakePageQuery(List.of(1L, 2L, 3L, 4L, 5L));
        LoanAccountIdReader reader = new LoanAccountIdReader(query::find, 2);

        // when
        List<Long> result = readAll(reader);

        // then
        assertThat(result).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(query.calls).containsExactly("0,2", "2,2", "4,2");
    }

    @Test
    @DisplayName("대상 ID 1~4를 페이지 크기 2로 읽으면 1~4 다음 null이고, 마지막 빈 페이지까지 3번 조회한다")
    void 페이지_크기의_배수면_빈_페이지에서_멈춘다() {
        // given
        FakePageQuery query = new FakePageQuery(List.of(1L, 2L, 3L, 4L));
        LoanAccountIdReader reader = new LoanAccountIdReader(query::find, 2);

        // when
        List<Long> result = readAll(reader);

        // then
        assertThat(result).containsExactly(1L, 2L, 3L, 4L);
        assertThat(query.calls).containsExactly("0,2", "2,2", "4,2");
    }

    @Test
    @DisplayName("대상이 없으면 첫 read가 null이고 1번만 조회한다")
    void 대상이_없으면_바로_끝난다() {
        // given
        FakePageQuery query = new FakePageQuery(List.of());
        LoanAccountIdReader reader = new LoanAccountIdReader(query::find, 2);

        // when
        Long result = reader.read();

        // then
        assertThat(result).isNull();
        assertThat(query.calls).containsExactly("0,2");
    }

    // null이 나올 때까지 읽는다. null 다음에도 다시 조회하지 않는지 한 번 더 읽어 본다.
    private List<Long> readAll(LoanAccountIdReader reader) {
        List<Long> ids = new ArrayList<>();
        for (Long id = reader.read(); id != null; id = reader.read()) {
            ids.add(id);
        }
        assertThat(reader.read()).isNull();
        return ids;
    }

    // ID 순 목록에서 마지막 ID보다 큰 것을 개수만큼 돌려주고, 받은 인자를 "마지막 ID,개수"로 기록한다.
    private static class FakePageQuery {

        private final List<Long> ids;
        private final List<String> calls = new ArrayList<>();

        FakePageQuery(List<Long> ids) {
            this.ids = ids;
        }

        List<Long> find(Long afterLoanAccountId, Integer limit) {
            calls.add(afterLoanAccountId + "," + limit);
            return ids.stream().filter(id -> id > afterLoanAccountId).limit(limit).toList();
        }
    }
}
