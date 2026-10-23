package com.repayment.batch.reader;

import org.springframework.batch.item.ItemReader;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 대출 ID를 keyset 페이징(마지막으로 읽은 ID 다음부터 100개씩)으로 읽는다.
 * 처리하면서 대상이 줄어도 OFFSET처럼 다음 페이지가 당겨지지 않아 건너뛰는 대출이 없다.
 * 일마감 Chunk Step의 Reader는 이 클래스를 상속해 대상 조회(findPage)만 정한다.
 */
public abstract class LoanAccountIdPagingReader implements ItemReader<Long> {

    private final int pageSize;

    private Iterator<Long> page = Collections.emptyIterator();
    private long lastLoanAccountId = 0;
    private boolean lastPage = false;

    protected LoanAccountIdPagingReader(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * afterLoanAccountId보다 큰 대상 대출 ID를 ID 순으로 limit개까지 돌려준다.
     */
    protected abstract List<Long> findPage(long afterLoanAccountId, int limit);

    @Override
    public Long read() {
        if (!page.hasNext()) {
            if (lastPage) {
                return null;
            }
            List<Long> ids = findPage(lastLoanAccountId, pageSize);
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
