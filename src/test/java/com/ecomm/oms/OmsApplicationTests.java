package com.ecomm.oms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring context boots end-to-end against H2 with Flyway applied.
 */
@SpringBootTest
@ActiveProfiles("test")
class OmsApplicationTests {

    @Test
    void contextLoads() {
        // Context startup (incl. Flyway migration + JPA validation) is the assertion.
    }
}
