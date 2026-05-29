package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-042 — GET /api/evaluations + GET /api/body/series.
 * Fixed-clock test config pins "now" to {@code 2026-05-27T12:00:00} so the
 * range windows are deterministic regardless of when CI runs.
 *
 * <p>Evaluations are inserted via {@link JdbcTemplate} with explicit
 * {@code evaluated_at} so the test can place rows inside / outside each
 * range window precisely.
 */
@Import(EvaluationListAndSeriesIT.FixedClockConfig.class)
class EvaluationListAndSeriesIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    /** "Now" — a Wednesday, same as the other fixed-clock tests. */
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 5, 27);

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;
    private long bobId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM evaluation_measurements");
        jdbc.update("DELETE FROM evaluations");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
    }

    // ── recent list ──────────────────────────────────────────────────────

    @Test
    void list_returns_empty_when_no_evaluations() {
        JsonNode body = json(list(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_returns_caller_evaluations_newest_first() {
        long e1 = insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(7));
        long e2 = insertEvaluation(aliceId, 79.5, FIXED_DATE.minusDays(3));
        long e3 = insertEvaluation(aliceId, 79.2, FIXED_DATE.minusDays(1));

        JsonNode body = json(list(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(3);
        // newest first (id DESC).
        assertThat(body.get("items").get(0).get("id").asLong()).isEqualTo(e3);
        assertThat(body.get("items").get(1).get("id").asLong()).isEqualTo(e2);
        assertThat(body.get("items").get(2).get("id").asLong()).isEqualTo(e1);
        // summary DTO omits measurements.
        assertThat(body.get("items").get(0).has("measurements")).isFalse();
        // rollup fields surface.
        assertThat(body.get("items").get(0).get("weightKg").decimalValue().doubleValue()).isEqualTo(79.2);
    }

    @Test
    void list_does_not_leak_other_users_evaluations() {
        insertEvaluation(aliceId, 80.0, FIXED_DATE);
        insertEvaluation(bobId, 75.0, FIXED_DATE);

        JsonNode alice = json(list(aliceToken, null, 20).getBody());
        assertThat(alice.get("items").size()).isEqualTo(1);
        assertThat(alice.get("items").get(0).get("weightKg").decimalValue().doubleValue()).isEqualTo(80.0);

        JsonNode bob = json(list(bobToken, null, 20).getBody());
        assertThat(bob.get("items").size()).isEqualTo(1);
        assertThat(bob.get("items").get(0).get("weightKg").decimalValue().doubleValue()).isEqualTo(75.0);
    }

    @Test
    void list_pagination_walks_with_cursor() {
        long e1 = insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(3));
        long e2 = insertEvaluation(aliceId, 79.5, FIXED_DATE.minusDays(2));
        long e3 = insertEvaluation(aliceId, 79.2, FIXED_DATE.minusDays(1));

        JsonNode page1 = json(list(aliceToken, null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(e3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(e2);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotNull();

        JsonNode page2 = json(list(aliceToken, Long.parseLong(cursor), 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(e1);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/evaluations",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── metric series — weight ──────────────────────────────────────────

    @Test
    void series_weight_returns_points_oldest_first_within_4w_window() {
        // 4w window = 28 days. Three rows inside, one outside.
        insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(40)); // outside 4w
        insertEvaluation(aliceId, 79.5, FIXED_DATE.minusDays(20)); // inside
        insertEvaluation(aliceId, 79.2, FIXED_DATE.minusDays(10)); // inside
        insertEvaluation(aliceId, 79.0, FIXED_DATE.minusDays(1));  // inside

        JsonNode body = json(series(aliceToken, "weight", "4w").getBody());
        assertThat(body.get("metric").asText()).isEqualTo("weight");
        assertThat(body.get("range").asText()).isEqualTo("4w");
        assertThat(body.get("unit").asText()).isEqualTo("kg");

        JsonNode points = body.get("points");
        assertThat(points.size()).isEqualTo(3);
        // Oldest → newest (ASC by evaluatedAt).
        assertThat(points.get(0).get("value").decimalValue().doubleValue()).isEqualTo(79.5);
        assertThat(points.get(1).get("value").decimalValue().doubleValue()).isEqualTo(79.2);
        assertThat(points.get(2).get("value").decimalValue().doubleValue()).isEqualTo(79.0);
    }

    @Test
    void series_weight_12w_includes_more_history() {
        insertEvaluation(aliceId, 82.0, FIXED_DATE.minusDays(80)); // inside 12w (84d), outside 4w
        insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(10)); // inside both
        insertEvaluation(aliceId, 90.0, FIXED_DATE.minusDays(200)); // outside 12w + 4w

        JsonNode w4 = json(series(aliceToken, "weight", "4w").getBody());
        assertThat(w4.get("points").size()).isEqualTo(1);
        JsonNode w12 = json(series(aliceToken, "weight", "12w").getBody());
        assertThat(w12.get("points").size()).isEqualTo(2);
    }

    @Test
    void series_body_fat_filters_null_rows() {
        // Weight-only row (no bf) + a manual row with bf — only the second
        // shows up in the body_fat series.
        long e1 = insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(5));
        long e2 = insertEvaluationWithBf(aliceId, 79.0, FIXED_DATE.minusDays(2), 18.4, "manual");

        JsonNode body = json(series(aliceToken, "body_fat", "4w").getBody());
        assertThat(body.get("unit").asText()).isEqualTo("%");
        JsonNode points = body.get("points");
        assertThat(points.size()).isEqualTo(1);
        assertThat(points.get(0).get("value").decimalValue().doubleValue()).isEqualTo(18.4);

        // Sanity: weight series sees both.
        JsonNode weight = json(series(aliceToken, "weight", "4w").getBody());
        assertThat(weight.get("points").size()).isEqualTo(2);
    }

    // ── metric series — point_id (measurements) ─────────────────────────

    @Test
    void series_point_id_returns_measurement_values_with_stored_unit() {
        long e1 = insertEvaluation(aliceId, 80.0, FIXED_DATE.minusDays(10));
        long e2 = insertEvaluation(aliceId, 79.5, FIXED_DATE.minusDays(5));
        long e3 = insertEvaluation(aliceId, 79.0, FIXED_DATE.minusDays(1));
        insertMeasurement(e1, "arm_r", "circumference", "cm", 36.0);
        insertMeasurement(e2, "arm_r", "circumference", "cm", 36.5);
        // e3 has no arm_r measurement — should be skipped (no point appended).
        insertMeasurement(e3, "neck", "circumference", "cm", 38.0);

        JsonNode body = json(series(aliceToken, "arm_r", "4w").getBody());
        assertThat(body.get("metric").asText()).isEqualTo("arm_r");
        assertThat(body.get("unit").asText()).isEqualTo("cm");
        JsonNode points = body.get("points");
        assertThat(points.size()).isEqualTo(2);
        assertThat(points.get(0).get("value").decimalValue().doubleValue()).isEqualTo(36.0);
        assertThat(points.get(1).get("value").decimalValue().doubleValue()).isEqualTo(36.5);
    }

    @Test
    void series_point_id_with_no_data_returns_empty_array_and_empty_unit() {
        // No evaluations at all → empty points, empty unit (we don't guess).
        JsonNode body = json(series(aliceToken, "tricep", "12w").getBody());
        assertThat(body.get("unit").asText()).isEmpty();
        assertThat(body.get("points").size()).isZero();
    }

    @Test
    void series_does_not_leak_other_users_data() {
        long bobEval = insertEvaluation(bobId, 70.0, FIXED_DATE.minusDays(5));
        insertMeasurement(bobEval, "arm_r", "circumference", "cm", 32.0);

        JsonNode body = json(series(aliceToken, "arm_r", "4w").getBody());
        assertThat(body.get("points").size()).isZero();
    }

    // ── validation ──────────────────────────────────────────────────────

    @Test
    void series_invalid_range_returns_400() {
        ResponseEntity<String> response = series(aliceToken, "weight", "all_time");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_RANGE.name());
    }

    @Test
    void series_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/body/series?metric=weight&range=4w",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
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

    private ResponseEntity<String> list(String token, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/evaluations?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("limit", limit);
        if (cursor != null) {
            template.append("&cursor={cursor}");
            vars.put("cursor", cursor);
        }
        return rest.exchange(
                template.toString(),
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class,
                vars);
    }

    private ResponseEntity<String> series(String token, String metric, String range) {
        return rest.exchange(
                "/api/body/series?metric={metric}&range={range}",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class,
                Map.of("metric", metric, "range", range));
    }

    private long insertEvaluation(long userId, double weightKg, LocalDate at) {
        java.time.OffsetDateTime ts = at.atTime(LocalTime.NOON)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        return jdbc.queryForObject(
                "INSERT INTO evaluations (user_id, weight_kg, evaluated_at) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, weightKg, ts);
    }

    private long insertEvaluationWithBf(long userId, double weightKg, LocalDate at,
                                        double bfPct, String bfMethod) {
        java.time.OffsetDateTime ts = at.atTime(LocalTime.NOON)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        return jdbc.queryForObject(
                "INSERT INTO evaluations (user_id, weight_kg, evaluated_at, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, userId, weightKg, ts, bfPct, bfMethod);
    }

    private void insertMeasurement(long evaluationId, String pointId, String kind, String unit, double value) {
        jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, ?, ?, ?, ?)",
                evaluationId, pointId, kind, unit, value);
    }

    /** Pins the service-layer clock to {@link #FIXED_DATE} (Wed 2026-05-27). */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    FIXED_DATE.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());
        }
    }
}
