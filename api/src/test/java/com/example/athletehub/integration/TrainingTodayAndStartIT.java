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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-032 — today's plan + start session.
 *
 * <p>Replaces the default {@code Clock} bean with a fixed clock pinned to
 * a Wednesday so test outcomes don't depend on the weekday CI happens to
 * run on. Templates and their schedules are seeded via {@link JdbcTemplate}
 * because there's no template-CRUD endpoint yet (lands later in Epic 7).
 */
@Import(TrainingTodayAndStartIT.FixedClockConfig.class)
class TrainingTodayAndStartIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String PASSWORD = "supersecret1!";

    /** 2026-05-27 is a Wednesday (day-of-week = 3 ISO). */
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 5, 27);
    private static final short WEDNESDAY = 3;
    private static final short THURSDAY = 4;

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private long aliceId;

    @BeforeEach
    void setUp() {
        // Wipe in dependency order — cascades from users handle the rest.
        jdbc.update("DELETE FROM session_exercises");
        jdbc.update("DELETE FROM workout_sessions");
        jdbc.update("DELETE FROM template_schedules");
        jdbc.update("DELETE FROM workout_template_exercises");
        jdbc.update("DELETE FROM workout_templates");
        // Keep the seeded global exercises; drop only customs.
        jdbc.update("DELETE FROM exercises WHERE created_by IS NOT NULL");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
    }

    // ── today's plan ──────────────────────────────────────────────────────

    @Test
    void today_returns_rest_day_when_no_schedule() {
        JsonNode body = json(today(aliceToken).getBody());
        assertThat(body.get("template").isNull()).isTrue();
        assertThat(body.get("activeSessionId").isNull()).isTrue();
    }

    @Test
    void today_returns_the_template_scheduled_for_today() {
        long benchId = exerciseIdByName("Bench Press");
        long ohpId = exerciseIdByName("Overhead Press");

        long templateId = createTemplate(aliceId, "Push A", "Chest + shoulders");
        addTemplateExercise(templateId, benchId, 0, "4 × 6-8", "80 kg");
        addTemplateExercise(templateId, ohpId, 1, "3 × 8", "40 kg");
        scheduleTemplate(templateId, WEDNESDAY);

        JsonNode body = json(today(aliceToken).getBody());
        assertThat(body.get("activeSessionId").isNull()).isTrue();
        JsonNode template = body.get("template");
        assertThat(template.isNull()).isFalse();
        assertThat(template.get("id").asLong()).isEqualTo(templateId);
        assertThat(template.get("name").asText()).isEqualTo("Push A");
        assertThat(template.get("description").asText()).isEqualTo("Chest + shoulders");

        JsonNode exercises = template.get("exercises");
        assertThat(exercises.size()).isEqualTo(2);
        assertThat(exercises.get(0).get("name").asText()).isEqualTo("Bench Press");
        assertThat(exercises.get(0).get("position").asInt()).isZero();
        assertThat(exercises.get(0).get("scheme").asText()).isEqualTo("4 × 6-8");
        assertThat(exercises.get(0).get("target").asText()).isEqualTo("80 kg");
        assertThat(exercises.get(1).get("name").asText()).isEqualTo("Overhead Press");
    }

    @Test
    void today_ignores_templates_scheduled_on_other_days() {
        long benchId = exerciseIdByName("Bench Press");

        long templateId = createTemplate(aliceId, "Tomorrow's plan", null);
        addTemplateExercise(templateId, benchId, 0, "4 × 6", null);
        scheduleTemplate(templateId, THURSDAY);

        JsonNode body = json(today(aliceToken).getBody());
        assertThat(body.get("template").isNull()).isTrue();
    }

    @Test
    void today_ignores_another_users_template_scheduled_for_today() {
        register("bob@example.com", "Bob B.", "bob.runs");
        long bobId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "bob@example.com");
        long benchId = exerciseIdByName("Bench Press");

        long bobTemplate = createTemplate(bobId, "Bob's plan", null);
        addTemplateExercise(bobTemplate, benchId, 0, "4 × 6", null);
        scheduleTemplate(bobTemplate, WEDNESDAY);

        JsonNode body = json(today(aliceToken).getBody());
        assertThat(body.get("template").isNull()).isTrue();
    }

    @Test
    void today_reports_active_session_id_when_one_is_in_progress() {
        // Start a session, then read today's plan.
        ResponseEntity<String> start = startSession(aliceToken, Map.of("title", "Quick Push"));
        long sessionId = json(start.getBody()).get("id").asLong();

        JsonNode body = json(today(aliceToken).getBody());
        assertThat(body.get("activeSessionId").asLong()).isEqualTo(sessionId);
    }

    // ── start session ────────────────────────────────────────────────────

    @Test
    void start_without_template_creates_an_empty_session() {
        ResponseEntity<String> response = startSession(aliceToken, Map.of("title", "Freestyle"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("title").asText()).isEqualTo("Freestyle");
        assertThat(dto.get("status").asText()).isEqualTo("in_progress");
        assertThat(dto.get("templateId").isNull()).isTrue();
        assertThat(dto.get("exercises").size()).isZero();
        assertThat(dto.get("totalSets").asInt()).isZero();
        assertThat(dto.get("prCount").asInt()).isZero();
    }

    @Test
    void start_with_template_seeds_session_exercises_in_order() {
        long benchId = exerciseIdByName("Bench Press");
        long ohpId = exerciseIdByName("Overhead Press");

        long templateId = createTemplate(aliceId, "Push A", null);
        addTemplateExercise(templateId, benchId, 0, "4 × 6", "80 kg");
        addTemplateExercise(templateId, ohpId, 1, "3 × 8", "40 kg");

        ResponseEntity<String> response = startSession(aliceToken, Map.of("templateId", templateId));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("title").asText()).isEqualTo("Push A");  // inherited from template
        assertThat(dto.get("templateId").asLong()).isEqualTo(templateId);

        JsonNode exercises = dto.get("exercises");
        assertThat(exercises.size()).isEqualTo(2);
        assertThat(exercises.get(0).get("exerciseId").asLong()).isEqualTo(benchId);
        assertThat(exercises.get(0).get("name").asText()).isEqualTo("Bench Press");
        assertThat(exercises.get(0).get("position").asInt()).isZero();
        assertThat(exercises.get(0).get("scheme").asText()).isEqualTo("4 × 6");
        assertThat(exercises.get(0).get("targetWeight").isNull()).isTrue();
        assertThat(exercises.get(1).get("exerciseId").asLong()).isEqualTo(ohpId);
    }

    @Test
    void start_rejects_when_another_session_is_already_in_progress() {
        startSession(aliceToken, Map.of("title", "First"));

        ResponseEntity<String> second = startSession(aliceToken, Map.of("title", "Second"));
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.ACTIVE_SESSION_EXISTS.name());
    }

    @Test
    void start_rejects_unknown_template() {
        ResponseEntity<String> response = startSession(aliceToken, Map.of("templateId", 99_999L));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.TEMPLATE_NOT_FOUND.name());
    }

    @Test
    void start_rejects_another_users_template() {
        register("bob@example.com", "Bob B.", "bob.runs");
        long bobId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "bob@example.com");
        long templateId = createTemplate(bobId, "Bob's plan", null);

        ResponseEntity<String> response = startSession(aliceToken, Map.of("templateId", templateId));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.TEMPLATE_NOT_FOUND.name());
    }

    @Test
    void start_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/workout-sessions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), jsonHeaders()),
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

    private ResponseEntity<String> today(String token) {
        return rest.exchange(
                "/api/training/today",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> startSession(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/workout-sessions",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private long exerciseIdByName(String name) {
        return jdbc.queryForObject(
                "SELECT id FROM exercises WHERE name = ? AND is_global = true", Long.class, name);
    }

    private long createTemplate(long ownerId, String name, String description) {
        Map<String, Object> args = new HashMap<>();
        args.put("ownerId", ownerId);
        args.put("name", name);
        args.put("description", description);
        return jdbc.queryForObject(
                "INSERT INTO workout_templates (owner_id, name, description) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, ownerId, name, description);
    }

    private void addTemplateExercise(long templateId, long exerciseId, int position,
                                     String scheme, String target) {
        jdbc.update(
                "INSERT INTO workout_template_exercises " +
                        "(template_id, exercise_id, position, scheme, target) VALUES (?, ?, ?, ?, ?)",
                templateId, exerciseId, position, scheme, target);
    }

    private void scheduleTemplate(long templateId, short dayOfWeek) {
        jdbc.update(
                "INSERT INTO template_schedules (template_id, day_of_week) VALUES (?, ?)",
                templateId, dayOfWeek);
    }

    /** Pins the service-layer clock to {@link #FIXED_DATE} (a Wednesday). */
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
