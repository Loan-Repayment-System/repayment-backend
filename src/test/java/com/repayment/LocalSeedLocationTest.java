package com.repayment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * local 프로필은 샘플 데이터 경로(db/seed/local)를 추가로 읽는다.
 * 상위 폴더(db/seed)를 지정하면 다른 환경의 seed까지 섞이므로 경로를 정확히 고정한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("local")
class LocalSeedLocationTest {

    @Autowired
    private FlywayProperties flywayProperties;

    @Test
    void local_프로필은_스키마와_로컬_시드_경로만_읽는다() {
        assertThat(flywayProperties.getLocations())
                .containsExactly("classpath:db/migration", "classpath:db/seed/local");
    }

    @Test
    void local_프로필은_순서가_뒤바뀐_마이그레이션을_허용한다() {
        assertThat(flywayProperties.isOutOfOrder()).isTrue();
    }
}
