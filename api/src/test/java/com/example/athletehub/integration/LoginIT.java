package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.service.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the AH-012 login flow on a real Spring context, real
 * PostgreSQL (Testcontainers), real Flyway, real Spring Security filter chain.
 * Covers happy path, wrong password, missing user, and the JWT-protected /me.
 */
class LoginIT extends AbstractIntegrationTest {

    private static final String EMAIL = "alex@example.com";
    private static final String PASSWORD = "supersecret1!";
    private static final String HANDLE = "alex.lifts";

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        // Pre-register a known user for the login tests
        register(Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "fullName", "Alex Carter",
                "handle", HANDLE));
    }

    @Test
    void login_returns_tokens_and_user() {
        ResponseEntity<String> response = login(Map.of("email", EMAIL, "password", PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("accessTokenExpiresIn").asLong()).isPositive();
        assertThat(body.get("user").get("email").asText()).isEqualTo(EMAIL);
        assertThat(body.get("user").get("handle").asText()).isEqualTo(HANDLE);

        // Refresh token persisted as a hash (not the plain value)
        String plainRefresh = body.get("refreshToken").asText();
        String tokenHash = RefreshTokenService.sha256Hex(plainRefresh);
        assertThat(refreshTokenRepository.findByTokenHash(tokenHash)).isPresent();
    }

    @Test
    void login_is_case_insensitive_on_email() {
        ResponseEntity<String> response =
                login(Map.of("email", "ALEX@example.com", "password", PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void login_returns_401_on_wrong_password() {
        ResponseEntity<String> response =
                login(Map.of("email", EMAIL, "password", "WRONG-pw!"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_CREDENTIALS.name());
    }

    @Test
    void login_returns_401_on_unknown_email_without_revealing_account_exists() {
        ResponseEntity<String> response =
                login(Map.of("email", "nobody@example.com", "password", PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_CREDENTIALS.name());
    }

    @Test
    void me_returns_401_without_token() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(null, jsonHeaders()), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void me_returns_authenticated_user_with_valid_access_token() {
        String accessToken = json(login(Map.of("email", EMAIL, "password", PASSWORD)).getBody())
                .get("accessToken").asText();

        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = rest.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(null, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode user = json(response.getBody());
        assertThat(user.get("email").asText()).isEqualTo(EMAIL);
        assertThat(user.get("handle").asText()).isEqualTo(HANDLE);
        assertThat(user.get("roles").get(0).asText()).isEqualTo("ATHLETE");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<String> register(Map<String, Object> body) {
        return rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> login(Map<String, Object> body) {
        return rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
    }
}
