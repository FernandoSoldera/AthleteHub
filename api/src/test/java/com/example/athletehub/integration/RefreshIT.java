package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.model.RefreshToken;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.service.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-013: refresh rotation, reuse detection, expiry, and
 * logout. Runs against the full Spring context + real PostgreSQL (Testcontainers)
 * + real Flyway + real Spring Security filter chain.
 */
class RefreshIT extends AbstractIntegrationTest {

    private static final String EMAIL = "alex@example.com";
    private static final String PASSWORD = "supersecret1!";
    private static final String HANDLE = "alex.lifts";

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    private String initialRefreshToken;
    private String initialAccessToken;

    @BeforeEach
    void clean_register_and_login() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "fullName", "Alex Carter",
                "handle", HANDLE));

        JsonNode loginBody = json(login(Map.of("email", EMAIL, "password", PASSWORD)).getBody());
        initialRefreshToken = loginBody.get("refreshToken").asText();
        initialAccessToken = loginBody.get("accessToken").asText();
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void refresh_returns_new_tokens_and_revokes_the_presented_one() {
        ResponseEntity<String> response = refresh(initialRefreshToken);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());

        String newAccess = body.get("accessToken").asText();
        String newRefresh = body.get("refreshToken").asText();
        assertThat(newAccess).isNotBlank().isNotEqualTo(initialAccessToken);
        assertThat(newRefresh).isNotBlank().isNotEqualTo(initialRefreshToken);

        // The presented refresh is now revoked; the new one is active.
        RefreshToken oldRow = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(initialRefreshToken)).orElseThrow();
        RefreshToken newRow = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(newRefresh)).orElseThrow();
        assertThat(oldRow.getRevokedAt()).isNotNull();
        assertThat(newRow.getRevokedAt()).isNull();
    }

    // ── reuse detection ────────────────────────────────────────────────────

    @Test
    void reusing_a_revoked_refresh_token_returns_401_and_revokes_all_active_tokens() {
        // First refresh succeeds and revokes the original.
        String newRefresh = json(refresh(initialRefreshToken).getBody()).get("refreshToken").asText();

        // Re-presenting the original (now revoked) refresh token is treated as compromise.
        ResponseEntity<String> response = refresh(initialRefreshToken);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_REFRESH_TOKEN.name());

        // The token issued after the rotation is now also revoked — the legitimate client must re-login.
        RefreshToken postRotation = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(newRefresh)).orElseThrow();
        assertThat(postRotation.getRevokedAt()).isNotNull();

        // And re-presenting the (now revoked) post-rotation token also fails.
        assertThat(refresh(newRefresh).getStatusCode().value()).isEqualTo(401);
    }

    // ── expiry ─────────────────────────────────────────────────────────────

    @Test
    void expired_refresh_token_returns_401() {
        // Backdate the token's expiry directly in the DB.
        RefreshToken row = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(initialRefreshToken)).orElseThrow();
        row.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(row);

        ResponseEntity<String> response = refresh(initialRefreshToken);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_REFRESH_TOKEN.name());
    }

    // ── unknown token ──────────────────────────────────────────────────────

    @Test
    void unknown_refresh_token_returns_401() {
        ResponseEntity<String> response = refresh("not-a-real-token-just-garbage");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_REFRESH_TOKEN.name());
    }

    // ── logout ─────────────────────────────────────────────────────────────

    @Test
    void logout_revokes_the_refresh_token_and_subsequent_refresh_fails() {
        ResponseEntity<String> logoutResponse = logout(initialRefreshToken);

        assertThat(logoutResponse.getStatusCode().value()).isEqualTo(204);

        RefreshToken row = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(initialRefreshToken)).orElseThrow();
        assertThat(row.getRevokedAt()).isNotNull();

        // The same token can no longer be refreshed.
        assertThat(refresh(initialRefreshToken).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void logout_with_unknown_token_is_idempotent_204() {
        ResponseEntity<String> response = logout("never-issued");
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<String> register(Map<String, Object> body) {
        return rest.postForEntity("/api/auth/register", new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> login(Map<String, Object> body) {
        return rest.postForEntity("/api/auth/login", new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> refresh(String refreshToken) {
        return rest.postForEntity(
                "/api/auth/token/refresh",
                new HttpEntity<>(Map.of("refreshToken", refreshToken), jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> logout(String refreshToken) {
        return rest.postForEntity(
                "/api/auth/logout",
                new HttpEntity<>(Map.of("refreshToken", refreshToken), jsonHeaders()),
                String.class);
    }
}
