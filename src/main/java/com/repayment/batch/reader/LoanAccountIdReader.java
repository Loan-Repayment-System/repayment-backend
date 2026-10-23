package com.repayment.batch.reader;

import org.springframework.batch.item.ItemReader;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 대출 ID를 keyset 페이징(마지막으로 읽은 ID 다음부터 pageSize개)으로 읽는다.
 * 처리하면서 대상이 줄어도 OFFSET처럼 다음 페이지가 당겨지지 않아 건너뛰는 대출이 없다.
 * 일마감 Chunk Step들이 대상 조회만 바꿔 함께 쓴다.
 */
public class LoanAccountIdReader implements ItemReader<Long> {

    // (마지막으로 읽은 ID, 개수) → 그 다음 대출 ID 목록(ID 순)
    private final BiFunction<Long, Integer, List<Long>> pageQuery;
    private final int pageSize;

    private Iterator<Long> page = Collections.emptyIterator();
    private long lastLoanAccountId = 0;
    private boolean lastPage = false;

    public LoanAccountIdReader(BiFunction<Long, Integer, List<Long>> pageQuery, int pageSize) {
        this.pageQuery = pageQuery;
        this.pageSize = pageSize;
    }

    @Override
    public Long read() {
        if (!page.hasNext()) {
            if (lastPage) {
                return null;
            }
            List<Long> ids = pageQuery.apply(lastLoanAccountId, pageSize);
            // 덜 찬 페이지면 다음 페이지는 없다.
            lastPage = ids.size() < pageSize;
            if (ids.isEmpty()) {
                return null;
            }
            page = ids.iterator();
        }
        lastLoanAccountId = page.next();
        return lastLoanAccountId;
    }
}
