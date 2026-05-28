package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the whole stack wires together: the context boots against a real
 * PostgreSQL with Flyway applied, and the actuator health endpoint reports UP.
 */
class SmokeIT extends AbstractIntegrationTest {

    @Test
    void contextLoads_andHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(json(response.getBody()).get("status").asText()).isEqualTo("UP");
    }
}
