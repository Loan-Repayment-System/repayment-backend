package com.repayment.domain.loan.mapper;

import com.repayment.TestcontainersConfig;
import com.repayment.domain.loan.dto.response.LoanContractResponse;
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
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("대출 계약 매퍼")
class LoanContractMapperIntegrationTest {

    @Autowired
    private LoanContractMapper loanContractMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("계약을 저장할 때")
    class 계약_저장 {

        @Test
        @DisplayName("10/17 계약, 만기 2027-10-17, 12,000,000원, 가산 1.5%, 기준 3.2%, 12회를 넣으면 만든 id를 채우고 그대로 저장한다")
        void 넣은_값을_저장하고_id를_채운다() {
            // given
            long memberId = insert("INSERT INTO member (login_id, password, name) VALUES ('contract-test', 'pw', '테스트')");
            long productId = insert("INSERT INTO loan_product (interest_type_id, repayment_method_id, name, additional_rate) "
                    + "VALUES (1, 2, '테스트 상품', 1.5)");
            LoanContractResponse contract = LoanContractResponse.builder()
                    .loanProductId(productId)
                    .memberId(memberId)
                    .contractDate(LocalDate.of(2026, 10, 17))
                    .maturityDate(LocalDate.of(2027, 10, 17))
                    .contractPrincipal(new BigDecimal("12000000"))
                    .contractAdditionalRate(new BigDecimal("1.5"))
                    .contractBaseRate(new BigDecimal("3.2"))
                    .totalInstallments(12)
                    .build();

            // when
            loanContractMapper.insertLoanContract(contract);

            // then
            assertThat(contract.getLoanContractId()).isNotNull();
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT loan_product_id, member_id, contract_date, "
                    + "maturity_date, contract_principal, contract_additional_rate, contract_base_rate, total_installments "
                    + "FROM loan_contract WHERE loan_contract_id = ?", contract.getLoanContractId());
            assertThat(((Number) row.get("loan_product_id")).longValue()).isEqualTo(productId);
            assertThat(((Number) row.get("member_id")).longValue()).isEqualTo(memberId);
            assertThat(toLocalDate(row.get("contract_date"))).isEqualTo(LocalDate.of(2026, 10, 17));
            assertThat(toLocalDate(row.get("maturity_date"))).isEqualTo(LocalDate.of(2027, 10, 17));
            assertThat((BigDecimal) row.get("contract_principal")).isEqualByComparingTo("12000000");
            assertThat((BigDecimal) row.get("contract_additional_rate")).isEqualByComparingTo("1.5");
            assertThat((BigDecimal) row.get("contract_base_rate")).isEqualByComparingTo("3.2");
            assertThat(row.get("total_installments")).isEqualTo(12);
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((Date) value).toLocalDate();
    }

    private long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
