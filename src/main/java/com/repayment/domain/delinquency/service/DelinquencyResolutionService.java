package com.repayment.domain.delinquency.service;

import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.domain.maturity.mapper.MaturityOverdueDailyChargeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대출 하나의 '진행중' 연체금 행 중 다 낸 행을 '해소'로 바꾼다.
 * 충당은 금액만 더하므로, 상환이 충당을 마친 뒤 부른다. 일마감은 부르지 않는다.
 * 대출 상태와 상관없이 세 테이블을 모두 본다. 해당하지 않는 테이블에는 바꿀 행이 없다.
 */
@Service
@RequiredArgsConstructor
public class DelinquencyResolutionService {

    private final InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;
    private final AccelerationDailyChargeMapper accelerationDailyChargeMapper;
    private final MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;

    @Transactional
    public void resolve(Long loanAccountId) {
        installmentDelinquencyChargeMapper.updateResolveFullyPaid(loanAccountId);
        accelerationDailyChargeMapper.updateResolveFullyPaid(loanAccountId);
        maturityOverdueDailyChargeMapper.updateResolveFullyPaid(loanAccountId);
    }
}
