package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-011 register flow: real Spring Boot context, real
 * PostgreSQL (Testcontainers), real Flyway migrations. Exercises the happy
 * path, the conflict path (case-insensitive), and validation rejection.
 */
class RegisterIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void register_creates_athlete_with_hashed_password() {
        Map<String, Object> body = Map.of(
                "email", "Alex@Example.com",
                "password", "supersecret1!",
                "fullName", "Alex Carter",
                "handle", "Alex.Lifts"
        );

        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("email").asText()).isEqualTo("alex@example.com");
        assertThat(dto.get("handle").asText()).isEqualTo("alex.lifts");
        assertThat(dto.get("fullName").asText()).isEqualTo("Alex Carter");
        assertThat(dto.get("id").asLong()).isPositive();

        Optional<User> saved = userRepository.findByEmail("alex@example.com");
        assertThat(saved).isPresent();
        User u = saved.get();
        assertThat(u.getPasswordHash()).startsWith("$2"); // BCrypt prefix
        assertThat(u.getRoles()).containsExactly(Role.ATHLETE);
        assertThat(u.getStatus()).isEqualTo("active");
        assertThat(u.getDateJoined()).isNotNull();
        assertThat(u.getCreatedAt()).isNotNull();
    }

    @Test
    void register_returns_409_on_duplicate_email_case_insensitive() {
        register(Map.of(
                "email", "dup@example.com",
                "password", "supersecret1!",
                "fullName", "First",
                "handle", "first.user"
        ));

        ResponseEntity<String> response = register(Map.of(
                "email", "DUP@example.com", // different case, same email
                "password", "anotherpw1!",
                "fullName", "Second",
                "handle", "second.user"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.EMAIL_ALREADY_REGISTERED.name());
    }

    @Test
    void register_returns_409_on_duplicate_handle() {
        register(Map.of(
                "email", "a@example.com",
                "password", "supersecret1!",
                "fullName", "A",
                "handle", "samehandle"
        ));

        ResponseEntity<String> response = register(Map.of(
                "email", "b@example.com",
                "password", "anotherpw1!",
                "fullName", "B",
                "handle", "SameHandle" // different case, same handle
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.HANDLE_ALREADY_TAKEN.name());
    }

    @Test
    void register_returns_400_on_invalid_payload() {
        // Map.of doesn't accept null values, but empty strings trigger @NotBlank.
        Map<String, Object> body = new HashMap<>();
        body.put("email", "not-an-email");
        body.put("password", "short");
        body.put("fullName", "");
        body.put("handle", "");

        ResponseEntity<String> response = register(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode root = json(response.getBody());
        assertThat(root.get("code").asText()).isEqualTo(MessageCode.VALIDATION_FAILED.name());
        assertThat(root.get("errors")).isNotNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<String> register(Map<String, Object> body) {
        return rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
    }
}
