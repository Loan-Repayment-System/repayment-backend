package com.repayment;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * db/migration의 V0~V2가 원본 스키마(docs/reference/ddl.sql)대로 적용되는지 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class FlywayMigrationTest {

    private static final List<String> DOMAIN_TABLES = List.of(
            "interest_type", "repayment_method", "loan_status",
            "repayment_schedule_status", "delinquency_status", "delinquency_reason",
            "member", "primary_account", "loan_product", "loan_contract",
            "loan_account", "loan_status_history",
            "repayment_schedule", "delinquency_history", "repayment_history");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 마이그레이션_V0부터_V2까지_성공으로_기록된다() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history)
                .extracting(row -> row.get("version"), row -> toBoolean(row.get("success")))
                .containsExactly(tuple("0", true), tuple("1", true), tuple("2", true));
    }

    @Test
    void 도메인_테이블이_모두_생성된다() {
        assertThat(findTableNames()).containsAll(DOMAIN_TABLES);
    }

    @Test
    void 스프링_배치_메타_테이블이_생성된다() {
        assertThat(findTableNames()).contains(
                "BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION_SEQ", "BATCH_JOB_EXECUTION_SEQ", "BATCH_JOB_SEQ");
    }

    @Test
    void 이자종류_코드가_원본과_같다() {
        assertCodes("SELECT interest_type_id AS id, name FROM interest_type ORDER BY id",
                tuple(1, "고정금리"), tuple(2, "변동금리"));
    }

    @Test
    void 상환방법_코드가_원본과_같다() {
        assertCodes("SELECT repayment_method_id AS id, name FROM repayment_method ORDER BY id",
                tuple(1, "원금만기일시상환"), tuple(2, "원리금균등상환"), tuple(3, "원금균등상환"));
    }

    @Test
    void 대출상태_코드가_원본과_같다() {
        assertCodes("SELECT loan_status_id AS id, name FROM loan_status ORDER BY id",
                tuple(1, "정상"), tuple(2, "연체"), tuple(3, "완제"), tuple(4, "기한이익상실"));
    }

    @Test
    void 상환스케줄상태_코드가_원본과_같다() {
        assertCodes("SELECT repayment_schedule_status_id AS id, name FROM repayment_schedule_status ORDER BY id",
                tuple(1, "예정"), tuple(2, "납부 기한 도래"), tuple(3, "납입 완료"), tuple(4, "연체"));
    }

    @Test
    void 연체상태_코드가_원본과_같다() {
        assertCodes("SELECT delinquency_status_id AS id, name FROM delinquency_status ORDER BY id",
                tuple(1, "진행중"), tuple(2, "해소"));
    }

    @Test
    void 연체사유_코드가_원본과_같다() {
        assertCodes("SELECT delinquency_reason_id AS id, name FROM delinquency_reason ORDER BY id",
                tuple(1, "만기일시 + 이자연체 + 1개월 미만"),
                tuple(2, "만기일시 + 이자연체 + 1개월 이상"),
                tuple(3, "만기일시 + 이자연체 + 1개월 이상 + ⑥항 적용"),
                tuple(4, "만기일시 + 원금연체"),
                tuple(5, "분할상환 + 연속 1회"),
                tuple(6, "분할상환 + 연속 2회 이상"),
                tuple(7, "분할상환 + 연속 2회 이상 + ⑥항 적용"));
    }

    private List<String> findTableNames() {
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
    }

    private void assertCodes(String sql, Tuple... expected) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        assertThat(rows)
                .extracting(row -> ((Number) row.get("id")).intValue(), row -> row.get("name"))
                .containsExactly(expected);
    }

    // MySQL 드라이버는 TINYINT(1)/BIT를 Boolean으로, 다른 설정에서는 숫자로 돌려줄 수 있다.
    private static boolean toBoolean(Object value) {
        return value instanceof Boolean b ? b : ((Number) value).intValue() == 1;
    }
}
