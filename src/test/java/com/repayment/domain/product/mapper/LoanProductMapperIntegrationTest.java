package com.repayment.domain.product.mapper;

import com.repayment.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("대출 상품 매퍼")
class LoanProductMapperIntegrationTest {

    @Autowired
    private LoanProductMapper loanProductMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("개설할 상품의 가산금리를 조회할 때")
    class 가산금리_조회 {

        @Test
        @DisplayName("가산금리 1.5% 상품이면 1.5%다")
        void 상품의_가산금리를_읽는다() {
            // given
            long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                    + "VALUES (1, 2, '테스트 상품', 1.5)");

            // when
            Optional<BigDecimal> result = loanProductMapper.findAdditionalRateById(productId);

            // then
            assertThat(result).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("1.5"));
        }

        @Test
        @DisplayName("상품이 없으면 빈 값이다")
        void 상품이_없으면_빈_값이다() {
            // when
            Optional<BigDecimal> result = loanProductMapper.findAdditionalRateById(-1L);

            // then
            assertThat(result).isEmpty();
        }
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
