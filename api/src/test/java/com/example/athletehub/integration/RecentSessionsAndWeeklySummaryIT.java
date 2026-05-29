package com.example.athletehub.integration;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-035 — recent sessions list + weekly cardio summary.
 *
 * <p>Reuses the fixed-clock pattern from AH-032 ({@code TrainingTodayAndStartIT})
 * with the pin moved to <strong>Wed 2026-05-27</strong> so this week is
 * Mon 2026-05-25 … Mon 2026-06-01 and last week is Mon 2026-05-18 … Mon
 * 2026-05-25. That lets us bucket cardio activities deterministically.
 */
@Import(RecentSessionsAndWeeklySummaryIT.FixedClockConfig.class)
class RecentSessionsAndWeeklySummaryIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 5, 27); // Wednesday
    private static final LocalDate THIS_WEEK_MONDAY = LocalDate.of(2026, 5, 25);
    private static final LocalDate LAST_WEEK_MONDAY = LocalDate.of(2026, 5, 18);

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;

    @BeforeEach
    void setUp() {
        // Wipe everything that touches the training tables; cascades from
        // users handle the rest.
        jdbc.update("DELETE FROM exercise_sets");
        jdbc.update("DELETE FROM session_exercises");
        jdbc.update("DELETE FROM workout_sessions");
        jdbc.update("DELETE FROM personal_records");
        jdbc.update("DELETE FROM cardio_activities");
        jdbc.update("DELETE FROM exercises WHERE created_by IS NOT NULL");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
    }

    // ── recent sessions ──────────────────────────────────────────────────

    @Test
    void list_returns_empty_when_no_sessions() {
        JsonNode body = json(recent(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_returns_caller_sessions_newest_first_including_in_progress() {
        // 1 completed + 1 in-progress. Both should appear, newest first.
        long completedId = insertSession(aliceId, "Push A", "completed", 1620.50, 12, 1);
        long inProgressId = insertSession(aliceId, "Push B", "in_progress", 0, 0, 0);

        JsonNode body = json(recent(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(2);
        assertThat(body.get("items").get(0).get("id").asLong()).isEqualTo(inProgressId);
        assertThat(body.get("items").get(0).get("status").asText()).isEqualTo("in_progress");
        assertThat(body.get("items").get(1).get("id").asLong()).isEqualTo(completedId);
        assertThat(body.get("items").get(1).get("status").asText()).isEqualTo("completed");
        // Rollup fields surface on the summary view.
        assertThat(body.get("items").get(1).get("totalVolumeKg").decimalValue().doubleValue()).isEqualTo(1620.50);
        assertThat(body.get("items").get(1).get("totalSets").asInt()).isEqualTo(12);
        assertThat(body.get("items").get(1).get("prCount").asInt()).isEqualTo(1);
    }

    @Test
    void list_does_not_leak_other_users_sessions() {
        insertSession(aliceId, "Alice's session", "completed", 100, 1, 0);
        long bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        insertSession(bobId, "Bob's session", "completed", 200, 2, 0);

        JsonNode alice = json(recent(aliceToken, null, 20).getBody());
        assertThat(alice.get("items").size()).isEqualTo(1);
        assertThat(alice.get("items").get(0).get("title").asText()).isEqualTo("Alice's session");

        JsonNode bob = json(recent(bobToken, null, 20).getBody());
        assertThat(bob.get("items").size()).isEqualTo(1);
        assertThat(bob.get("items").get(0).get("title").asText()).isEqualTo("Bob's session");
    }

    @Test
    void list_pagination_walks_with_cursor() {
        long s1 = insertSession(aliceId, "1", "completed", 100, 1, 0);
        long s2 = insertSession(aliceId, "2", "completed", 200, 2, 0);
        long s3 = insertSession(aliceId, "3", "completed", 300, 3, 0);

        JsonNode page1 = json(recent(aliceToken, null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(s3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(s2);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotNull();

        JsonNode page2 = json(recent(aliceToken, Long.parseLong(cursor), 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(s1);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_summary_payload_omits_exercises_and_sets() {
        insertSession(aliceId, "Lean Push", "completed", 100, 1, 0);
        JsonNode body = json(recent(aliceToken, null, 20).getBody());
        // The summary DTO has no exercises field — keeps a 20-row page from
        // dragging hydrated children. (The full session view lands later.)
        assertThat(body.get("items").get(0).has("exercises")).isFalse();
    }

    @Test
    void list_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/workout-sessions",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── weekly summary ───────────────────────────────────────────────────

    @Test
    void weekly_summary_reports_zero_when_no_cardio() {
        JsonNode body = json(weekly(aliceToken).getBody());
        assertThat(body.get("thisWeekKm").decimalValue().intValue()).isZero();
        assertThat(body.get("lastWeekKm").decimalValue().intValue()).isZero();
        assertThat(body.get("deltaKm").decimalValue().intValue()).isZero();
    }

    @Test
    void weekly_summary_sums_distance_in_meters_and_returns_km() {
        // This week: 5 000 m on Mon, 8 000 m on Wed → 13 km
        insertCardio(aliceId, "run", 5000, 1800, THIS_WEEK_MONDAY.atTime(7, 0));
        insertCardio(aliceId, "run", 8000, 2400, THIS_WEEK_MONDAY.plusDays(2).atTime(18, 0));
        // Last week: 10 000 m total
        insertCardio(aliceId, "cycle", 10000, 3000, LAST_WEEK_MONDAY.atTime(8, 0));

        JsonNode body = json(weekly(aliceToken).getBody());
        assertThat(body.get("thisWeekKm").decimalValue().doubleValue()).isEqualTo(13.00);
        assertThat(body.get("lastWeekKm").decimalValue().doubleValue()).isEqualTo(10.00);
        assertThat(body.get("deltaKm").decimalValue().doubleValue()).isEqualTo(3.00);
    }

    @Test
    void weekly_summary_negative_delta_when_this_week_is_lower() {
        insertCardio(aliceId, "run", 2000, 600, THIS_WEEK_MONDAY.atTime(7, 0));
        insertCardio(aliceId, "run", 5000, 1500, LAST_WEEK_MONDAY.atTime(7, 0));

        JsonNode body = json(weekly(aliceToken).getBody());
        assertThat(body.get("thisWeekKm").decimalValue().doubleValue()).isEqualTo(2.00);
        assertThat(body.get("lastWeekKm").decimalValue().doubleValue()).isEqualTo(5.00);
        assertThat(body.get("deltaKm").decimalValue().doubleValue()).isEqualTo(-3.00);
    }

    @Test
    void weekly_summary_ignores_activities_outside_the_two_week_window() {
        // Two weeks ago → ignored entirely.
        insertCardio(aliceId, "run", 999000, 7200, LAST_WEEK_MONDAY.minusDays(7).atTime(7, 0));
        // The week AFTER this (future) → ignored.
        insertCardio(aliceId, "run", 999000, 7200, THIS_WEEK_MONDAY.plusDays(8).atTime(7, 0));
        // This week.
        insertCardio(aliceId, "run", 4000, 1200, THIS_WEEK_MONDAY.atTime(7, 0));

        JsonNode body = json(weekly(aliceToken).getBody());
        assertThat(body.get("thisWeekKm").decimalValue().doubleValue()).isEqualTo(4.00);
        assertThat(body.get("lastWeekKm").decimalValue().doubleValue()).isZero();
    }

    @Test
    void weekly_summary_does_not_leak_other_users_cardio() {
        long bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        insertCardio(bobId, "run", 50000, 9000, THIS_WEEK_MONDAY.atTime(7, 0));

        JsonNode body = json(weekly(aliceToken).getBody());
        assertThat(body.get("thisWeekKm").decimalValue().intValue()).isZero();
    }

    @Test
    void weekly_summary_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/training/weekly-summary",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── helpers ──────────────────────────────────────────────────────────

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

    private ResponseEntity<String> recent(String token, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/workout-sessions?limit={limit}");
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

    private ResponseEntity<String> weekly(String token) {
        return rest.exchange(
                "/api/training/weekly-summary",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private long insertSession(long userId, String title, String status,
                               double volume, int sets, int prCount) {
        return jdbc.queryForObject(
                "INSERT INTO workout_sessions " +
                        "(user_id, title, status, total_volume_kg, total_sets, pr_count) " +
                        "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, userId, title, status, volume, sets, prCount);
    }

    private void insertCardio(long userId, String type, int distanceM, int duration,
                              java.time.LocalDateTime startedAt) {
        java.time.OffsetDateTime started = startedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        jdbc.update(
                "INSERT INTO cardio_activities " +
                        "(user_id, type, distance_m, duration_seconds, started_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                userId, type, distanceM, duration, started);
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
