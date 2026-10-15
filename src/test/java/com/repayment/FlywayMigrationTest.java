package com.repayment;

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
 * db/migration의 V0~V6가 빈 DB에 오류 없이 적용되는지 검증한다.
 * 테이블 구성은 docs/erd.md를 따른다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 마이그레이션_V0부터_V7까지_성공으로_기록된다() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history)
                .extracting(row -> row.get("version"), row -> toBoolean(row.get("success")))
                .containsExactly(tuple("0", true), tuple("1", true), tuple("2", true), tuple("3", true), tuple("4", true), tuple("5", true),
                        tuple("6", true), tuple("7", true));
    }

    @Test
    void 스프링_배치_메타_테이블이_생성된다() {
        assertThat(findTableNames()).contains(
                "BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION_SEQ", "BATCH_JOB_EXECUTION_SEQ", "BATCH_JOB_SEQ");
    }

    private List<String> findTableNames() {
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
    }

    // MySQL 드라이버는 TINYINT(1)/BIT를 Boolean으로, 다른 설정에서는 숫자로 돌려줄 수 있다.
    private static boolean toBoolean(Object value) {
        return value instanceof Boolean b ? b : ((Number) value).intValue() == 1;
    }
}
