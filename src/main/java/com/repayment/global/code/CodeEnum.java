package com.repayment.global.code;

/**
 * 코드성 테이블과 1:1인 enum. DB에는 상수 이름이나 순서가 아니라 id로 저장한다.
 * 새 enum을 만들면 CodeEnumTypeHandler의 @MappedTypes에도 추가한다.
 */
public interface CodeEnum {

    int getId();

    // 코드성 테이블의 name 컬럼과 같은 한글 이름
    String getDescription();
}
