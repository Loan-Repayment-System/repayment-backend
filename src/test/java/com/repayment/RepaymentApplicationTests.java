package com.repayment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class RepaymentApplicationTests {

    @Test
    void 애플리케이션_컨텍스트가_기동된다() {
    }
}
