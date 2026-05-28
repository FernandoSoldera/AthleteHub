package com.example.athletehub.integration;

import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-016 — PATCH /api/me (partial profile update) and
 * POST /api/me/roles/switch (role grant + active-role flip).
 */
class ProfileIT extends AbstractIntegrationTest {

    private static final String EMAIL = "alex@example.com";
    private static final String PASSWORD = "supersecret1!";
    private static final String HANDLE = "alex.lifts";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private String accessToken;

    @BeforeEach
    void registerAndLogin() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "fullName", "Alex Carter",
                "handle", HANDLE));

        JsonNode loginBody = json(login(EMAIL, PASSWORD).getBody());
        accessToken = loginBody.get("accessToken").asText();
    }

    // ── PATCH /me ──────────────────────────────────────────────────────────

    @Test
    void patch_me_partial_update_only_changes_fields_that_were_sent() {
        // First set a full profile via PATCH.
        Map<String, Object> initial = new HashMap<>();
        initial.put("fullName", "Alex C.");
        initial.put("bio", "Strength + hypertrophy");
        initial.put("age", 29);
        initial.put("heightCm", 178.0);
        initial.put("avatarHue", 220);

        ResponseEntity<String> first = patchMe(initial);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        JsonNode firstBody = json(first.getBody());
        assertThat(firstBody.get("fullName").asText()).isEqualTo("Alex C.");
        assertThat(firstBody.get("bio").asText()).isEqualTo("Strength + hypertrophy");
        assertThat(firstBody.get("age").asInt()).isEqualTo(29);
        assertThat(firstBody.get("heightCm").asDouble()).isEqualTo(178.0);
        assertThat(firstBody.get("avatarHue").asInt()).isEqualTo(220);

        // Now PATCH with only bio. fullName / age / heightCm / avatarHue must remain.
        ResponseEntity<String> second = patchMe(Map.of("bio", "PR chaser."));
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        JsonNode secondBody = json(second.getBody());
        assertThat(secondBody.get("bio").asText()).isEqualTo("PR chaser.");
        assertThat(secondBody.get("fullName").asText()).isEqualTo("Alex C.");
        assertThat(secondBody.get("age").asInt()).isEqualTo(29);
        assertThat(secondBody.get("heightCm").asDouble()).isEqualTo(178.0);
        assertThat(secondBody.get("avatarHue").asInt()).isEqualTo(220);
    }

    @Test
    void patch_me_rejects_invalid_values_with_400() {
        ResponseEntity<String> response = patchMe(Map.of(
                "age", 200,                // > 129
                "avatarHue", 999,          // > 359
                "fullName", ""));          // blank

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void patch_me_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("bio", "anything"), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── POST /me/roles/switch ──────────────────────────────────────────────

    @Test
    void switch_to_coach_grants_the_coach_role_on_first_use() {
        // After signup the user only has ATHLETE.
        JsonNode meBefore = json(me().getBody());
        assertThat(asList(meBefore.get("roles"))).containsExactly("ATHLETE");

        ResponseEntity<String> response = switchRole("COACH");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(asList(json(response.getBody()).get("roles")))
                .containsExactlyInAnyOrder("ATHLETE", "COACH");

        // Persisted.
        var stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getRoles().stream().map(Enum::name))
                .containsExactlyInAnyOrder("ATHLETE", "COACH");
    }

    @Test
    void switch_to_athlete_is_a_noop_for_someone_who_is_already_an_athlete() {
        JsonNode body = json(switchRole("ATHLETE").getBody());
        assertThat(asList(body.get("roles"))).containsExactly("ATHLETE");
    }

    @Test
    void switch_role_without_token_returns_401() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/me/roles/switch",
                new HttpEntity<>(Map.of("role", "COACH"), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static java.util.List<String> asList(JsonNode arrayNode) {
        java.util.List<String> out = new java.util.ArrayList<>();
        arrayNode.forEach(n -> out.add(n.asText()));
        return out;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private ResponseEntity<String> register(Map<String, Object> body) {
        return rest.postForEntity("/api/auth/register", new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> me() {
        return rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(null, authHeaders()), String.class);
    }

    private ResponseEntity<String> patchMe(Map<String, Object> body) {
        return rest.exchange("/api/me", HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()), String.class);
    }

    private ResponseEntity<String> switchRole(String role) {
        return rest.postForEntity(
                "/api/me/roles/switch",
                new HttpEntity<>(Map.of("role", role), authHeaders()),
                String.class);
    }
}
