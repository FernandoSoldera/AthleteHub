package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-041 — POST /api/evaluations + GET
 * /api/evaluations/{id}. Covers the four creation shapes (weight-only,
 * manual, jackson_pollock_7, navy), the schema XOR rule via the service,
 * specific error codes for missing inputs, and per-user visibility.
 *
 * <p>Each test that needs a profile (sex / age / height) sets it via
 * PATCH /me after registration so we don't add those fields to the
 * standard signup helper.
 */
class EvaluationsIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM evaluation_measurements");
        jdbc.update("DELETE FROM evaluations");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
    }

    // ── weight-only ──────────────────────────────────────────────────────

    @Test
    void create_weight_only_persists_with_bf_fields_null() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 82.5));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("weightKg").decimalValue().doubleValue()).isEqualTo(82.5);
        assertThat(dto.get("bodyFatPct").isNull()).isTrue();
        assertThat(dto.get("bfMethod").isNull()).isTrue();
        assertThat(dto.get("source").asText()).isEqualTo("self");
        assertThat(dto.get("measurements").size()).isZero();
    }

    @Test
    void create_weight_only_still_stores_measurements_when_supplied() {
        // The Evolution time-series graphs want raw measurements even on a
        // weight-only check-in, so we don't drop them on the floor.
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 82.5,
                "measurements", List.of(
                        Map.of("pointId", "arm_r", "kind", "circumference", "unit", "cm", "value", 36.0))));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("measurements").size()).isEqualTo(1);
        assertThat(dto.get("bfMethod").isNull()).isTrue();
    }

    // ── manual ──────────────────────────────────────────────────────────

    @Test
    void create_manual_passes_through_supplied_pct() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "manual",
                "bodyFatPct", 18.4));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("bfMethod").asText()).isEqualTo("manual");
        assertThat(dto.get("bodyFatPct").decimalValue().doubleValue()).isEqualTo(18.4);
    }

    @Test
    void create_manual_without_pct_returns_400() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "manual"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_MANUAL_REQUIRES_PCT.name());
    }

    // ── Jackson-Pollock 7-site ──────────────────────────────────────────

    @Test
    void create_jp7_computes_body_fat_from_skinfolds_age_and_sex() {
        // Profile-required inputs.
        patchMe(aliceToken, Map.of("sex", "male", "age", 30));

        // Seven 10mm skinfolds → sum = 70.
        List<Map<String, Object>> skinfolds = new ArrayList<>();
        for (String point : List.of("chest", "abdomen", "thigh", "tricep",
                "subscapular", "suprailiac", "midaxillary")) {
            skinfolds.add(Map.of(
                    "pointId", point, "kind", "skinfold", "unit", "mm", "value", 10.0));
        }
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "jackson_pollock_7",
                "measurements", skinfolds));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("bfMethod").asText()).isEqualTo("jackson_pollock_7");
        // Expected ≈ 10.2% — assert a tight range so the formula can't drift.
        double pct = dto.get("bodyFatPct").decimalValue().doubleValue();
        assertThat(pct).isBetween(9.0, 11.5);
        assertThat(dto.get("measurements").size()).isEqualTo(7);
    }

    @Test
    void create_jp7_missing_skinfold_returns_400() {
        patchMe(aliceToken, Map.of("sex", "male", "age", 30));

        // Drop "midaxillary" — only 6 of 7 supplied.
        List<Map<String, Object>> skinfolds = new ArrayList<>();
        for (String point : List.of("chest", "abdomen", "thigh", "tricep",
                "subscapular", "suprailiac")) {
            skinfolds.add(Map.of(
                    "pointId", point, "kind", "skinfold", "unit", "mm", "value", 10.0));
        }
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "jackson_pollock_7",
                "measurements", skinfolds));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_MISSING_MEASUREMENTS.name());
    }

    @Test
    void create_jp7_without_user_sex_returns_400_bf_missing_user_field() {
        // Sex not set on Alice.
        patchMe(aliceToken, Map.of("age", 30));

        List<Map<String, Object>> skinfolds = new ArrayList<>();
        for (String point : List.of("chest", "abdomen", "thigh", "tricep",
                "subscapular", "suprailiac", "midaxillary")) {
            skinfolds.add(Map.of(
                    "pointId", point, "kind", "skinfold", "unit", "mm", "value", 10.0));
        }
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "jackson_pollock_7",
                "measurements", skinfolds));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_MISSING_USER_FIELD.name());
    }

    // ── Navy ────────────────────────────────────────────────────────────

    @Test
    void create_navy_male_computes_body_fat_from_neck_waist_height() {
        patchMe(aliceToken, Map.of("sex", "male", "heightCm", 180.0));

        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "navy",
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 40.0),
                        Map.of("pointId", "waist", "kind", "circumference", "unit", "cm", "value", 80.0))));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("bfMethod").asText()).isEqualTo("navy");
        // Expected ≈ 10.3% for neck 40 / waist 80 / height 180 (cm body-density form + Siri).
        double pct = dto.get("bodyFatPct").decimalValue().doubleValue();
        assertThat(pct).isBetween(8.0, 13.0);
    }

    @Test
    void create_navy_female_requires_hip_circumference() {
        patchMe(aliceToken, Map.of("sex", "female", "heightCm", 165.0));

        // Without hip → 400 BF_MISSING_MEASUREMENTS.
        ResponseEntity<String> missingHip = create(aliceToken, Map.of(
                "weightKg", 65.0,
                "bfMethod", "navy",
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 32.0),
                        Map.of("pointId", "waist", "kind", "circumference", "unit", "cm", "value", 70.0))));
        assertThat(missingHip.getStatusCode().value()).isEqualTo(400);
        assertThat(json(missingHip.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_MISSING_MEASUREMENTS.name());

        // With hip → 201 and a plausible value.
        ResponseEntity<String> ok = create(aliceToken, Map.of(
                "weightKg", 65.0,
                "bfMethod", "navy",
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 32.0),
                        Map.of("pointId", "waist", "kind", "circumference", "unit", "cm", "value", 70.0),
                        Map.of("pointId", "hip", "kind", "circumference", "unit", "cm", "value", 95.0))));
        assertThat(ok.getStatusCode().value()).isEqualTo(201);
        double pct = json(ok.getBody()).get("bodyFatPct").decimalValue().doubleValue();
        assertThat(pct).isBetween(15.0, 35.0);
    }

    @Test
    void create_navy_without_user_height_returns_400() {
        patchMe(aliceToken, Map.of("sex", "male"));
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "navy",
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 40.0),
                        Map.of("pointId", "waist", "kind", "circumference", "unit", "cm", "value", 80.0))));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_MISSING_USER_FIELD.name());
    }

    // ── durnin (not yet supported) ──────────────────────────────────────

    @Test
    void create_durnin_returns_400_bf_method_not_supported() {
        patchMe(aliceToken, Map.of("sex", "male", "age", 30));
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "durnin",
                "measurements", List.of(
                        Map.of("pointId", "tricep", "kind", "skinfold", "unit", "mm", "value", 10.0))));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.BF_METHOD_NOT_SUPPORTED.name());
    }

    // ── validation ──────────────────────────────────────────────────────

    @Test
    void create_rejects_unknown_bf_method() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "bfMethod", "bod_pod"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void create_rejects_duplicate_point_id_in_measurements() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "weightKg", 80.0,
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 40.0),
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 41.0))));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void create_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/evaluations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("weightKg", 80.0), jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── GET single ──────────────────────────────────────────────────────

    @Test
    void get_returns_caller_evaluation_with_measurements() {
        long id = createAndId(aliceToken, Map.of(
                "weightKg", 80.0,
                "measurements", List.of(
                        Map.of("pointId", "neck", "kind", "circumference", "unit", "cm", "value", 38.0))));

        ResponseEntity<String> response = rest.exchange(
                "/api/evaluations/" + id,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("id").asLong()).isEqualTo(id);
        assertThat(dto.get("measurements").size()).isEqualTo(1);
        assertThat(dto.get("measurements").get(0).get("pointId").asText()).isEqualTo("neck");
    }

    @Test
    void get_on_another_users_evaluation_returns_404() {
        long id = createAndId(aliceToken, Map.of("weightKg", 80.0));

        ResponseEntity<String> response = rest.exchange(
                "/api/evaluations/" + id,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(bobToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.EVALUATION_NOT_FOUND.name());
    }

    @Test
    void get_unknown_returns_404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/evaluations/999999",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void register(String email, String fullName, String handle) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", fullName,
                        "handle", handle), jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    private String authToken(String email) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD), jsonHeaders()),
                String.class);
        return json(response.getBody()).get("accessToken").asText();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private void patchMe(String token, Map<String, Object> body) {
        ResponseEntity<String> response = rest.exchange(
                "/api/me",
                HttpMethod.PATCH,
                new HttpEntity<>(body, bearer(token)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> create(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/evaluations",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private long createAndId(String token, Map<String, Object> body) {
        ResponseEntity<String> response = create(token, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }
}
