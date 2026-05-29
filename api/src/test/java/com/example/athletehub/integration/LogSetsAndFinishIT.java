package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserCountersRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-033 — granular set ops + finish session.
 *
 * <p>Covers the patch idempotency contract, ownership checks, the rollup
 * math (total_volume_kg, total_sets), PR detection on e1RM (Epley) and
 * max_weight, the {@code is_pr} flag on the responsible set, the
 * user_counters.sessions increment on finish, and the in_progress →
 * completed status flip.
 *
 * <p>Sessions are kicked off via {@code POST /api/workout-sessions} so
 * the seeding of session_exercises from a template (AH-032) is exercised
 * alongside.
 */
class LogSetsAndFinishIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserCountersRepository userCountersRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;
    private long benchId;
    private long ohpId;
    private long sessionId;
    private long benchSeId;
    private long ohpSeId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM exercise_sets");
        jdbc.update("DELETE FROM session_exercises");
        jdbc.update("DELETE FROM workout_sessions");
        jdbc.update("DELETE FROM personal_records");
        jdbc.update("DELETE FROM template_schedules");
        jdbc.update("DELETE FROM workout_template_exercises");
        jdbc.update("DELETE FROM workout_templates");
        jdbc.update("DELETE FROM exercises WHERE created_by IS NOT NULL");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);

        benchId = exerciseIdByName("Bench Press");
        ohpId = exerciseIdByName("Overhead Press");

        // Seed a Push A template and start a session from it so every test
        // has two session_exercises (bench + OHP) to log against.
        long templateId = createTemplate(aliceId, "Push A");
        addTemplateExercise(templateId, benchId, 0);
        addTemplateExercise(templateId, ohpId, 1);
        JsonNode session = json(startSession(aliceToken, Map.of("templateId", templateId)).getBody());
        sessionId = session.get("id").asLong();
        JsonNode exercises = session.get("exercises");
        benchSeId = exercises.get(0).get("id").asLong();
        ohpSeId = exercises.get(1).get("id").asLong();
    }

    // ── PATCH: idempotency + ownership ────────────────────────────────────

    @Test
    void patch_upsert_inserts_then_updates_the_same_set() {
        // First op: insert set #1 at 80 × 8 (not done).
        JsonNode afterInsert = json(patch(aliceToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", benchSeId,
                "setNumber", 1,
                "weightKg", 80,
                "reps", 8,
                "done", false))).getBody());
        JsonNode insertSets = findExerciseSets(afterInsert, benchSeId);
        assertThat(insertSets.size()).isEqualTo(1);
        assertThat(insertSets.get(0).get("weightKg").decimalValue().intValue()).isEqualTo(80);
        assertThat(insertSets.get(0).get("done").asBoolean()).isFalse();

        // Same op key (sessionExerciseId, setNumber) → updates, not duplicates.
        JsonNode afterUpdate = json(patch(aliceToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", benchSeId,
                "setNumber", 1,
                "weightKg", 82.5,
                "reps", 7,
                "done", true))).getBody());
        JsonNode updateSets = findExerciseSets(afterUpdate, benchSeId);
        assertThat(updateSets.size()).isEqualTo(1); // still one set
        assertThat(updateSets.get(0).get("weightKg").decimalValue().doubleValue()).isEqualTo(82.5);
        assertThat(updateSets.get(0).get("reps").asInt()).isEqualTo(7);
        assertThat(updateSets.get(0).get("done").asBoolean()).isTrue();
        assertThat(updateSets.get(0).get("completedAt").isNull()).isFalse();
    }

    @Test
    void patch_delete_removes_the_set_and_is_idempotent_on_missing() {
        JsonNode afterInsert = json(patch(aliceToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", benchSeId,
                "setNumber", 1,
                "weightKg", 80, "reps", 8, "done", true))).getBody());
        assertThat(findExerciseSets(afterInsert, benchSeId).size()).isEqualTo(1);

        JsonNode afterDelete = json(patch(aliceToken, sessionId, List.of(Map.of(
                "op", "delete",
                "sessionExerciseId", benchSeId,
                "setNumber", 1))).getBody());
        assertThat(findExerciseSets(afterDelete, benchSeId).size()).isZero();

        // Idempotent: deleting again is fine, not a 4xx.
        ResponseEntity<String> response = patch(aliceToken, sessionId, List.of(Map.of(
                "op", "delete",
                "sessionExerciseId", benchSeId,
                "setNumber", 1)));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void patch_applies_multiple_ops_atomically() {
        JsonNode body = json(patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 80, "reps", 8, "done", true),
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 2, "weightKg", 80, "reps", 7, "done", true),
                Map.of("op", "upsert", "sessionExerciseId", ohpSeId, "setNumber", 1, "weightKg", 40, "reps", 10, "done", true))).getBody());

        assertThat(findExerciseSets(body, benchSeId).size()).isEqualTo(2);
        assertThat(findExerciseSets(body, ohpSeId).size()).isEqualTo(1);
    }

    @Test
    void patch_rejects_op_pointing_at_another_sessions_exercise() {
        // Bob starts his own session — its session_exercises are foreign to Alice.
        long bobTemplate = createTemplate(jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL), "Bob's plan");
        addTemplateExercise(bobTemplate, benchId, 0);
        long bobSe = json(startSession(bobToken, Map.of("templateId", bobTemplate)).getBody())
                .get("exercises").get(0).get("id").asLong();

        ResponseEntity<String> response = patch(aliceToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", bobSe,
                "setNumber", 1,
                "weightKg", 80, "reps", 8, "done", true)));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_SET_OP.name());
    }

    @Test
    void patch_on_another_users_session_returns_404() {
        ResponseEntity<String> response = patch(bobToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", benchSeId,
                "setNumber", 1,
                "weightKg", 80, "reps", 8, "done", true)));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.SESSION_NOT_FOUND.name());
    }

    @Test
    void patch_on_completed_session_returns_409() {
        finish(aliceToken, sessionId);
        ResponseEntity<String> response = patch(aliceToken, sessionId, List.of(Map.of(
                "op", "upsert",
                "sessionExerciseId", benchSeId,
                "setNumber", 1,
                "weightKg", 80, "reps", 8, "done", true)));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.SESSION_NOT_IN_PROGRESS.name());
    }

    @Test
    void patch_with_unknown_op_returns_400() {
        ResponseEntity<String> response = patch(aliceToken, sessionId, List.of(Map.of(
                "op", "drop_the_bar",
                "sessionExerciseId", benchSeId,
                "setNumber", 1)));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_SET_OP.name());
    }

    // ── FINISH: rollups + PRs + counters ──────────────────────────────────

    @Test
    void finish_computes_total_volume_and_total_sets() {
        // Bench: 80×8 done, 80×7 done, 80×6 not done.
        // OHP: 40×10 done. Expected total_sets = 3, volume = 80*8 + 80*7 + 40*10 = 1600.
        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 80, "reps", 8, "done", true),
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 2, "weightKg", 80, "reps", 7, "done", true),
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 3, "weightKg", 80, "reps", 6, "done", false),
                Map.of("op", "upsert", "sessionExerciseId", ohpSeId, "setNumber", 1, "weightKg", 40, "reps", 10, "done", true)));

        JsonNode finished = json(finish(aliceToken, sessionId).getBody());
        assertThat(finished.get("status").asText()).isEqualTo("completed");
        assertThat(finished.get("totalSets").asInt()).isEqualTo(3);
        assertThat(finished.get("totalVolumeKg").decimalValue().intValue()).isEqualTo(1600);
        assertThat(finished.get("durationSeconds").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void finish_creates_e1rm_and_max_weight_prs_when_none_existed() {
        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 100, "reps", 5, "done", true)));

        JsonNode finished = json(finish(aliceToken, sessionId).getBody());
        // Two PRs (e1rm + max_weight) for the same exercise, one set responsible.
        assertThat(finished.get("prCount").asInt()).isEqualTo(2);
        JsonNode set = finished.get("exercises").get(0).get("sets").get(0);
        assertThat(set.get("pr").asBoolean()).isTrue();

        // Persisted PRs: e1rm = 100 * (1 + 5/30) = 116.67; max_weight = 100.
        Integer prRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM personal_records WHERE user_id = ? AND exercise_id = ?",
                Integer.class, aliceId, benchId);
        assertThat(prRows).isEqualTo(2);
    }

    @Test
    void finish_does_not_create_pr_when_no_improvement() {
        // Seed an existing better PR.
        jdbc.update(
                "INSERT INTO personal_records (user_id, exercise_id, metric, value, achieved_at) " +
                        "VALUES (?, ?, 'e1rm', 200, NOW()), (?, ?, 'max_weight', 200, NOW())",
                aliceId, benchId, aliceId, benchId);

        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 80, "reps", 5, "done", true)));

        JsonNode finished = json(finish(aliceToken, sessionId).getBody());
        assertThat(finished.get("prCount").asInt()).isZero();
        assertThat(finished.get("exercises").get(0).get("sets").get(0).get("pr").asBoolean()).isFalse();

        // Prior PR values stayed put.
        java.math.BigDecimal e1rm = jdbc.queryForObject(
                "SELECT value FROM personal_records WHERE user_id = ? AND exercise_id = ? AND metric = 'e1rm'",
                java.math.BigDecimal.class, aliceId, benchId);
        assertThat(e1rm.intValue()).isEqualTo(200);
    }

    @Test
    void finish_flags_each_responsible_set_for_two_separate_pr_metrics() {
        // Bench: set #1 = 100×3 (e1rm ≈ 110); set #2 = 95×5 (e1rm ≈ 110.83 — beats #1 on e1rm,
        // but #1 wins on max_weight). Both sets should get is_pr.
        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 100, "reps", 3, "done", true),
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 2, "weightKg", 95, "reps", 5, "done", true)));

        JsonNode finished = json(finish(aliceToken, sessionId).getBody());
        assertThat(finished.get("prCount").asInt()).isEqualTo(2);
        JsonNode sets = finished.get("exercises").get(0).get("sets");
        // Both sets flagged — different metrics, different winning sets.
        long prFlagged = 0;
        for (JsonNode s : sets) if (s.get("pr").asBoolean()) prFlagged++;
        assertThat(prFlagged).isEqualTo(2);
    }

    @Test
    void finish_increments_user_sessions_counter() {
        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 80, "reps", 8, "done", true)));

        int before = userCountersRepository.findById(aliceId).orElseThrow().getSessions();
        finish(aliceToken, sessionId);
        int after = userCountersRepository.findById(aliceId).orElseThrow().getSessions();
        assertThat(after - before).isEqualTo(1);
    }

    @Test
    void finish_ignores_undone_sets_in_rollups_and_pr_detection() {
        // Heavy lift left at done=false — must not produce a PR or count.
        patch(aliceToken, sessionId, List.of(
                Map.of("op", "upsert", "sessionExerciseId", benchSeId, "setNumber", 1, "weightKg", 500, "reps", 1, "done", false)));

        JsonNode finished = json(finish(aliceToken, sessionId).getBody());
        assertThat(finished.get("totalSets").asInt()).isZero();
        assertThat(finished.get("totalVolumeKg").decimalValue().intValue()).isZero();
        assertThat(finished.get("prCount").asInt()).isZero();

        Integer prRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM personal_records WHERE user_id = ?", Integer.class, aliceId);
        assertThat(prRows).isZero();
    }

    @Test
    void finish_on_completed_session_returns_409() {
        finish(aliceToken, sessionId);
        ResponseEntity<String> second = finish(aliceToken, sessionId);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.SESSION_NOT_IN_PROGRESS.name());
    }

    @Test
    void finish_on_another_users_session_returns_404() {
        ResponseEntity<String> response = finish(bobToken, sessionId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.SESSION_NOT_FOUND.name());
    }

    @Test
    void finish_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/workout-sessions/" + sessionId + "/finish",
                HttpMethod.POST,
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

    private long exerciseIdByName(String name) {
        return jdbc.queryForObject(
                "SELECT id FROM exercises WHERE name = ? AND is_global = true", Long.class, name);
    }

    private long createTemplate(long ownerId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO workout_templates (owner_id, name) VALUES (?, ?) RETURNING id",
                Long.class, ownerId, name);
    }

    private void addTemplateExercise(long templateId, long exerciseId, int position) {
        jdbc.update(
                "INSERT INTO workout_template_exercises (template_id, exercise_id, position) VALUES (?, ?, ?)",
                templateId, exerciseId, position);
    }

    private ResponseEntity<String> startSession(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/workout-sessions",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> patch(String token, long sessionId, List<Map<String, Object>> ops) {
        Map<String, Object> body = new HashMap<>();
        body.put("sets", ops);
        return rest.exchange(
                "/api/workout-sessions/" + sessionId,
                HttpMethod.PATCH,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> finish(String token, long sessionId) {
        return rest.exchange(
                "/api/workout-sessions/" + sessionId + "/finish",
                HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    /**
     * Returns the sets array for {@code seId} from the session DTO returned
     * by PATCH or POST .../finish — there's no dedicated GET endpoint until
     * AH-035, but PATCH/finish responses are full session DTOs, so reading
     * state inline keeps the tests honest.
     */
    private static JsonNode findExerciseSets(JsonNode session, long seId) {
        for (JsonNode se : session.get("exercises")) {
            if (se.get("id").asLong() == seId) return se.get("sets");
        }
        throw new AssertionError("session_exercise " + seId + " missing from response");
    }
}
