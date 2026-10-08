package com.learnerview.chitchat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the modular package layout wires up cleanly
 * (security, tenancy, realtime, all module services).
 */
@SpringBootTest(properties = {
        "app.jwtSecret=test-secret-for-context-load-only",
        "spring.data.mongodb.auto-index-creation=false"
})
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
