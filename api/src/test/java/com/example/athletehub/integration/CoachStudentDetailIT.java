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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-073 — coach's deep-dive student detail. Covers
 * the visibility matrix (only the coach on an active relationship can
 * see the detail; pending / ended / wrong-coach / wrong-direction all
 * 404) and the composition (relationship row + athlete hydrated +
 * recent sessions + weekly cardio + latest evaluation, with reasonable
 * empty-state behaviour).
 *
 * <p>Fixed-clock test config pins "now" to Wed 2026-05-27 so the weekly
 * cardio window is deterministic; same pattern as AH-035's
 * RecentSessionsAndWeeklySummaryIT.
 */
@Import(CoachStudentDetailIT.FixedClockConfig.class)
class CoachStudentDetailIT extends AbstractIntegrationTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 5, 27);
    private static final LocalDate THIS_WEEK_MONDAY = LocalDate.of(2026, 5, 25);

    private static final String COACH_EMAIL = "coach@example.com";
    private static final String OTHER_COACH_EMAIL = "coach2@example.com";
    private static final String ATHLETE_EMAIL = "athlete@example.com";
    private static final String STRANGER_EMAIL = "stranger@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String coachToken;
    private String otherCoachToken;
    private String athleteToken;
    private String strangerToken;

    private long coachId;
    private long otherCoachId;
    private long athleteId;
    private long strangerId;

    @BeforeEach
    void setUp() {
        // Wipe everything that touches the user-CASCADE chains.
        jdbc.update("DELETE FROM evaluation_measurements");
        jdbc.update("DELETE FROM evaluations");
        jdbc.update("DELETE FROM exercise_sets");
        jdbc.update("DELETE FROM session_exercises");
        jdbc.update("DELETE FROM workout_sessions");
        jdbc.update("DELETE FROM cardio_activities");
        jdbc.update("DELETE FROM coach_athlete");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(COACH_EMAIL, "Coach One", "coach.one");
        register(OTHER_COACH_EMAIL, "Coach Two", "coach.two");
        register(ATHLETE_EMAIL, "Alex Athlete", "alex.athlete");
        register(STRANGER_EMAIL, "Stranger", "stranger");

        coachId = idOf(COACH_EMAIL);
        otherCoachId = idOf(OTHER_COACH_EMAIL);
        athleteId = idOf(ATHLETE_EMAIL);
        strangerId = idOf(STRANGER_EMAIL);
        coachToken = authToken(COACH_EMAIL);
        otherCoachToken = authToken(OTHER_COACH_EMAIL);
        athleteToken = authToken(ATHLETE_EMAIL);
        strangerToken = authToken(STRANGER_EMAIL);
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    void coach_can_see_their_active_athletes_full_detail() {
        long relId = insertRelationship(coachId, athleteId, "active", "on_track", 88, "Cut · 78 kg");
        // Seed a workout session, a cardio activity, and an evaluation.
        insertSession(athleteId, "Push A", "completed", 1620.50, 12, 1);
        insertCardio(athleteId, "run", 5000, 1500, FIXED_DATE.minusDays(2).atTime(7, 0));
        long evalId = insertEvaluation(athleteId, 80.0, FIXED_DATE.minusDays(1));

        JsonNode dto = json(studentDetail(coachToken, athleteId).getBody());

        // Relationship row passed through verbatim.
        assertThat(dto.get("relationshipId").asLong()).isEqualTo(relId);
        assertThat(dto.get("status").asText()).isEqualTo("active");
        assertThat(dto.get("flag").asText()).isEqualTo("on_track");
        assertThat(dto.get("adherencePct").asInt()).isEqualTo(88);
        assertThat(dto.get("goal").asText()).isEqualTo("Cut · 78 kg");

        // Athlete hydrated.
        assertThat(dto.get("athlete").get("handle").asText()).isEqualTo("alex.athlete");

        // Latest evaluation surfaces.
        assertThat(dto.get("latestEvaluation").get("id").asLong()).isEqualTo(evalId);
        assertThat(dto.get("latestEvaluation").get("weightKg").decimalValue().doubleValue())
                .isEqualTo(80.0);

        // Weekly cardio totals reflect the 5 km run that landed inside the window.
        assertThat(dto.get("weeklyCardio").get("thisWeekKm").decimalValue().doubleValue())
                .isEqualTo(5.0);
        assertThat(dto.get("weeklyCardio").get("lastWeekKm").decimalValue().intValue()).isZero();

        // Recent sessions list has the one we seeded.
        assertThat(dto.get("recentSessions").size()).isEqualTo(1);
        assertThat(dto.get("recentSessions").get(0).get("title").asText()).isEqualTo("Push A");
        assertThat(dto.get("recentSessions").get(0).get("totalSets").asInt()).isEqualTo(12);
    }

    @Test
    void empty_states_pass_through_cleanly() {
        insertRelationship(coachId, athleteId, "active", null, null, null);
        // No sessions, no cardio, no evaluations.

        JsonNode dto = json(studentDetail(coachToken, athleteId).getBody());

        assertThat(dto.get("flag").isNull()).isTrue();
        assertThat(dto.get("adherencePct").isNull()).isTrue();
        assertThat(dto.get("latestEvaluation").isNull()).isTrue();
        assertThat(dto.get("recentSessions").size()).isZero();
        // weeklyCardio is never null — zeros when no data.
        assertThat(dto.get("weeklyCardio").get("thisWeekKm").decimalValue().intValue()).isZero();
        assertThat(dto.get("weeklyCardio").get("lastWeekKm").decimalValue().intValue()).isZero();
        assertThat(dto.get("weeklyCardio").get("deltaKm").decimalValue().intValue()).isZero();
    }

    @Test
    void recent_sessions_caps_at_5() {
        insertRelationship(coachId, athleteId, "active", null, null, null);
        for (int i = 0; i < 8; i++) {
            insertSession(athleteId, "Session " + i, "completed", 100, 1, 0);
        }

        JsonNode dto = json(studentDetail(coachToken, athleteId).getBody());
        assertThat(dto.get("recentSessions").size()).isEqualTo(5);
    }

    // ── visibility matrix ────────────────────────────────────────────────

    @Test
    void another_coach_cannot_see_someone_elses_athlete() {
        insertRelationship(coachId, athleteId, "active", null, null, null);

        ResponseEntity<String> response = studentDetail(otherCoachToken, athleteId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void pending_relationship_does_not_count_as_active() {
        insertRelationship(coachId, athleteId, "pending", null, null, null);

        ResponseEntity<String> response = studentDetail(coachToken, athleteId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void ended_relationship_does_not_count_as_active() {
        insertRelationship(coachId, athleteId, "ended", null, null, null);

        ResponseEntity<String> response = studentDetail(coachToken, athleteId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void athlete_calling_their_own_detail_returns_404() {
        // The path is /api/coach/athletes/{id} — semantically "me viewing my
        // athlete". The athlete viewing themselves at this path is meaningless
        // (and they don't have an active coach_athlete row where they are the
        // coach), so → 404 by the same visibility rule.
        insertRelationship(coachId, athleteId, "active", null, null, null);

        ResponseEntity<String> response = studentDetail(athleteToken, athleteId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void coach_calling_for_athlete_with_no_relationship_returns_404() {
        // No relationship at all — coach hasn't invited stranger.
        ResponseEntity<String> response = studentDetail(coachToken, strangerId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void unknown_athlete_id_returns_404() {
        ResponseEntity<String> response = studentDetail(coachToken, 999_999L);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/coach/athletes/1", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── data isolation across athletes ───────────────────────────────────

    @Test
    void detail_only_surfaces_rollups_for_the_target_athlete() {
        insertRelationship(coachId, athleteId, "active", null, null, null);
        // Stranger has lots of sessions; should not appear on the athlete's detail.
        insertSession(strangerId, "Stranger's session", "completed", 999, 99, 99);

        JsonNode dto = json(studentDetail(coachToken, athleteId).getBody());
        assertThat(dto.get("recentSessions").size()).isZero();
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

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
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

    private long insertRelationship(long coachId, long athleteId, String status,
                                    String flag, Integer adherencePct, String goal) {
        return jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status, flag, adherence_pct, goal) " +
                        "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, coachId, athleteId, status, flag, adherencePct, goal);
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
        OffsetDateTime ts = startedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        jdbc.update(
                "INSERT INTO cardio_activities " +
                        "(user_id, type, distance_m, duration_seconds, started_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                userId, type, distanceM, duration, ts);
    }

    private long insertEvaluation(long userId, double weightKg, LocalDate at) {
        OffsetDateTime ts = at.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault())
                .toOffsetDateTime();
        return jdbc.queryForObject(
                "INSERT INTO evaluations (user_id, weight_kg, evaluated_at) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, weightKg, ts);
    }

    private ResponseEntity<String> studentDetail(String token, long athleteId) {
        return rest.exchange(
                "/api/coach/athletes/" + athleteId, HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class);
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
