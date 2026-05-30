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
 * End-to-end test of AH-061. Covers manual post create + soft-delete +
 * the three auto-publish hooks (finishSession → workout post,
 * cardio.create → run/cycle post, evaluation.create → evolution post) +
 * the user_counters.posts counter math + visibility default + author-
 * scoped delete.
 */
class PostsIT extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // Wipe in dependency order — feed first, then training, eval, etc.
        jdbc.update("DELETE FROM post_comments");
        jdbc.update("DELETE FROM post_likes");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM evaluation_measurements");
        jdbc.update("DELETE FROM evaluations");
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

    // ── manual posts ─────────────────────────────────────────────────────

    @Test
    void manual_post_create_returns_201_with_defaults() {
        ResponseEntity<String> response = post(aliceToken, Map.of(
                "title", "Big day",
                "note", "Hit a 3-plate bench for the first time."));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("authorId").asLong()).isEqualTo(aliceId);
        assertThat(dto.get("type").asText()).isEqualTo("manual");
        assertThat(dto.get("title").asText()).isEqualTo("Big day");
        assertThat(dto.get("note").asText()).isEqualTo("Hit a 3-plate bench for the first time.");
        assertThat(dto.get("visibility").asText()).isEqualTo("followers");
        assertThat(dto.get("sourceRefType").isNull()).isTrue();
        assertThat(dto.get("sourceRefId").isNull()).isTrue();
        assertThat(dto.get("likeCount").asInt()).isZero();
        assertThat(dto.get("commentCount").asInt()).isZero();
    }

    @Test
    void manual_post_accepts_visibility_override() {
        for (String v : new String[]{"public", "followers", "private"}) {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "T");
            body.put("visibility", v);
            JsonNode dto = json(post(aliceToken, body).getBody());
            assertThat(dto.get("visibility").asText()).as("visibility %s", v).isEqualTo(v);
        }
    }

    @Test
    void manual_post_rejects_bad_visibility() {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "T");
        body.put("visibility", "everyone");
        ResponseEntity<String> response = post(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void manual_post_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/posts", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "x"), jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void manual_post_increments_user_counters_posts() {
        int before = userCountersRepository.findById(aliceId).orElseThrow().getPosts();
        post(aliceToken, Map.of("title", "T"));
        post(aliceToken, Map.of("title", "U"));
        int after = userCountersRepository.findById(aliceId).orElseThrow().getPosts();
        assertThat(after - before).isEqualTo(2);
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void delete_soft_deletes_the_post_and_decrements_counter() {
        long postId = json(post(aliceToken, Map.of("title", "T")).getBody()).get("id").asLong();
        int afterCreate = userCountersRepository.findById(aliceId).orElseThrow().getPosts();

        ResponseEntity<String> response = rest.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        // Row still exists with deleted_at set.
        java.sql.Timestamp deletedAt = jdbc.queryForObject(
                "SELECT deleted_at FROM posts WHERE id = ?",
                java.sql.Timestamp.class, postId);
        assertThat(deletedAt).isNotNull();
        // Counter decremented.
        int afterDelete = userCountersRepository.findById(aliceId).orElseThrow().getPosts();
        assertThat(afterCreate - afterDelete).isEqualTo(1);
    }

    @Test
    void delete_already_deleted_returns_404() {
        long postId = json(post(aliceToken, Map.of("title", "T")).getBody()).get("id").asLong();
        deletePost(aliceToken, postId);

        ResponseEntity<String> second = rest.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(404);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.POST_NOT_FOUND.name());
    }

    @Test
    void delete_another_users_post_returns_404() {
        long postId = json(post(aliceToken, Map.of("title", "T")).getBody()).get("id").asLong();

        ResponseEntity<String> response = rest.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(bobToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);

        // Alice's post is untouched (still active).
        java.sql.Timestamp deletedAt = jdbc.queryForObject(
                "SELECT deleted_at FROM posts WHERE id = ?",
                java.sql.Timestamp.class, postId);
        assertThat(deletedAt).isNull();
    }

    @Test
    void delete_unknown_returns_404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/posts/999999", HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── auto-publish from finishSession ──────────────────────────────────

    @Test
    void finishing_a_workout_session_emits_a_workout_post() {
        // Set up: create exercises catalog row + a session via the public
        // start endpoint, log one set, then finish.
        long benchId = jdbc.queryForObject(
                "SELECT id FROM exercises WHERE name = 'Bench Press' AND is_global = true",
                Long.class);
        long templateId = jdbc.queryForObject(
                "INSERT INTO workout_templates (owner_id, name) VALUES (?, 'Push A') RETURNING id",
                Long.class, aliceId);
        jdbc.update(
                "INSERT INTO workout_template_exercises (template_id, exercise_id, position) " +
                        "VALUES (?, ?, 0)", templateId, benchId);

        JsonNode startResp = json(rest.exchange(
                "/api/workout-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("templateId", templateId), bearer(aliceToken)),
                String.class).getBody());
        long sessionId = startResp.get("id").asLong();
        long benchSeId = startResp.get("exercises").get(0).get("id").asLong();

        rest.exchange(
                "/api/workout-sessions/" + sessionId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sets", List.of(
                        Map.of("op", "upsert", "sessionExerciseId", benchSeId,
                                "setNumber", 1, "weightKg", 100, "reps", 5, "done", true))),
                        bearer(aliceToken)),
                String.class);

        ResponseEntity<String> finishResp = rest.exchange(
                "/api/workout-sessions/" + sessionId + "/finish", HttpMethod.POST,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(finishResp.getStatusCode().value()).isEqualTo(200);

        // A workout post lives in the DB now.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT type, source_ref_type, source_ref_id, author_id, visibility " +
                        "FROM posts WHERE author_id = ?", aliceId);
        assertThat(row.get("type")).isEqualTo("workout");
        assertThat(row.get("source_ref_type")).isEqualTo("workout_session");
        assertThat(((Number) row.get("source_ref_id")).longValue()).isEqualTo(sessionId);
        assertThat(row.get("author_id")).isEqualTo(aliceId);
        assertThat(row.get("visibility")).isEqualTo("followers");
        // Counter went up by 1.
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getPosts())
                .isEqualTo(1);
    }

    // ── auto-publish from cardio.create ──────────────────────────────────

    @Test
    void creating_a_cardio_activity_emits_a_run_post() {
        Map<String, Object> body = Map.of(
                "type", "run",
                "distanceM", 5000,
                "durationSeconds", 1500,
                "avgHr", 150);
        ResponseEntity<String> create = rest.exchange(
                "/api/cardio-activities", HttpMethod.POST,
                new HttpEntity<>(body, bearer(aliceToken)), String.class);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        long cardioId = json(create.getBody()).get("id").asLong();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT type, source_ref_type, source_ref_id FROM posts WHERE author_id = ?",
                aliceId);
        assertThat(row.get("type")).isEqualTo("run");
        assertThat(row.get("source_ref_type")).isEqualTo("cardio_activity");
        assertThat(((Number) row.get("source_ref_id")).longValue()).isEqualTo(cardioId);
    }

    @Test
    void creating_a_cycle_cardio_emits_a_cycle_post() {
        rest.exchange(
                "/api/cardio-activities", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "cycle",
                        "distanceM", 20000,
                        "durationSeconds", 3600), bearer(aliceToken)),
                String.class);
        String type = jdbc.queryForObject(
                "SELECT type FROM posts WHERE author_id = ?", String.class, aliceId);
        assertThat(type).isEqualTo("cycle");
    }

    @Test
    void creating_a_walk_cardio_emits_a_run_post() {
        // Walk events render the same card as runs — the design's post
        // type enum doesn't distinguish; we tag walk → run.
        rest.exchange(
                "/api/cardio-activities", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "walk",
                        "distanceM", 1500,
                        "durationSeconds", 900), bearer(aliceToken)),
                String.class);
        String type = jdbc.queryForObject(
                "SELECT type FROM posts WHERE author_id = ?", String.class, aliceId);
        assertThat(type).isEqualTo("run");
    }

    // ── auto-publish from evaluation.create ──────────────────────────────

    @Test
    void creating_an_evaluation_emits_an_evolution_post() {
        ResponseEntity<String> create = rest.exchange(
                "/api/evaluations", HttpMethod.POST,
                new HttpEntity<>(Map.of("weightKg", 82.5), bearer(aliceToken)),
                String.class);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        long evalId = json(create.getBody()).get("id").asLong();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT type, source_ref_type, source_ref_id FROM posts WHERE author_id = ?",
                aliceId);
        assertThat(row.get("type")).isEqualTo("evolution");
        assertThat(row.get("source_ref_type")).isEqualTo("evaluation");
        assertThat(((Number) row.get("source_ref_id")).longValue()).isEqualTo(evalId);
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

    private ResponseEntity<String> post(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/posts", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);
    }

    private void deletePost(String token, long postId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
