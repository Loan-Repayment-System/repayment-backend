package com.repayment.global.config.typehandler;

import com.repayment.global.code.CodeEnum;
import com.repayment.global.code.DailyClosingStatus;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.FailureType;
import com.repayment.global.code.InterestType;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.code.RepaymentMethod;
import com.repayment.global.code.RepaymentScheduleStatus;
import com.repayment.global.code.RepaymentTargetType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 코드성 enum을 DB의 id 컬럼으로 바꾼다. mybatis.type-handlers-package로 등록되고,
 * MyBatis가 @MappedTypes의 enum마다 이 클래스를 하나씩 만든다.
 */
@MappedTypes({
        InterestType.class,
        RepaymentMethod.class,
        LoanStatus.class,
        RepaymentScheduleStatus.class,
        LoanJudgmentStatus.class,
        DelinquencyStatus.class,
        RepaymentChannel.class,
        DailyClosingStatus.class,
        FailureType.class,
        RepaymentTargetType.class
})
public class CodeEnumTypeHandler<E extends Enum<E> & CodeEnum> extends BaseTypeHandler<E> {

    private final Class<E> type;

    public CodeEnumTypeHandler(Class<E> type) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getId());
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int id = rs.getInt(columnName);
        return rs.wasNull() ? null : toEnum(id);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int id = rs.getInt(columnIndex);
        return rs.wasNull() ? null : toEnum(id);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int id = cs.getInt(columnIndex);
        return cs.wasNull() ? null : toEnum(id);
    }

    // 없는 id를 null로 바꾸면 잘못된 데이터가 조용히 넘어가므로 예외를 던진다.
    private E toEnum(int id) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getId() == id) {
                return constant;
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + "에 id " + id + "인 코드가 없습니다.");
    }
}
