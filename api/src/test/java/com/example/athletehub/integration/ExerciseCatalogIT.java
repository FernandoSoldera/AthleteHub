package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.ExerciseRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-031 — the exercise catalog. Exercises:
 *
 * <ul>
 *   <li>the seed migration loaded the global catalog (34 rows),</li>
 *   <li>the search endpoint matches case-insensitively on substring,</li>
 *   <li>cursor pagination is stable and signals "more pages" correctly,</li>
 *   <li>a custom exercise is visible to its owner but not to other users,</li>
 *   <li>the create endpoint stamps {@code is_global = false / created_by =
 *       caller}, rejects duplicate names from the same caller, and accepts
 *       a global-name "fork" because that's intentional.</li>
 * </ul>
 *
 * <p>Custom rows added by tests are cleaned up between runs by deleting
 * exercises with a non-null {@code created_by}; the seeded globals stay
 * untouched (their {@code created_by} is NULL).
 */
class ExerciseCatalogIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ExerciseRepository exerciseRepository;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        // Drop only user-created customs; keep the seed catalog intact.
        exerciseRepository.findAll().stream()
                .filter(e -> e.getCreatedBy() != null)
                .forEach(exerciseRepository::delete);

        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
    }

    // ── seed ──────────────────────────────────────────────────────────────

    @Test
    void seed_migration_loaded_the_global_catalog() {
        JsonNode body = json(search(aliceToken, null, null, 100).getBody());
        // The seed inserts 34 rows; assert "at least 30" so the test doesn't
        // turn red the next time someone tunes the seed list by ±a few.
        assertThat(body.get("items").size()).isGreaterThanOrEqualTo(30);
        // Every returned row is a non-custom (global) for a user with no customs.
        body.get("items").forEach(item ->
                assertThat(item.get("custom").asBoolean()).isFalse());
    }

    @Test
    void search_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/exercises",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── search ────────────────────────────────────────────────────────────

    @Test
    void search_matches_case_insensitive_substring() {
        JsonNode body = json(search(aliceToken, "bench", null, 100).getBody());
        // "Bench Press", "Incline Bench Press", "Dumbbell Bench Press" — all 3 globals.
        assertThat(body.get("items").size()).isEqualTo(3);
        body.get("items").forEach(item ->
                assertThat(item.get("name").asText().toLowerCase()).contains("bench"));
    }

    @Test
    void blank_query_returns_full_catalog() {
        JsonNode body = json(search(aliceToken, "   ", null, 100).getBody());
        // Empty q after trim → service treats it as no filter, like null.
        assertThat(body.get("items").size()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void pagination_uses_cursor_to_walk_all_pages() {
        // Page 1, limit 10
        JsonNode page1 = json(search(aliceToken, null, null, 10).getBody());
        assertThat(page1.get("items").size()).isEqualTo(10);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotNull();
        long lastId = page1.get("items").get(9).get("id").asLong();

        // Page 2 picks up exactly after the cursor.
        JsonNode page2 = json(search(aliceToken, null, Long.parseLong(cursor), 10).getBody());
        assertThat(page2.get("items").size()).isEqualTo(10);
        assertThat(page2.get("items").get(0).get("id").asLong()).isGreaterThan(lastId);

        // Pages don't overlap.
        long page2FirstId = page2.get("items").get(0).get("id").asLong();
        page1.get("items").forEach(item ->
                assertThat(item.get("id").asLong()).isLessThan(page2FirstId));
    }

    // ── custom create + visibility ────────────────────────────────────────

    @Test
    void create_marks_custom_and_owner_can_see_it_but_other_users_cannot() {
        ResponseEntity<String> create = create(aliceToken, Map.of(
                "name", "Wide-Grip Bench",
                "category", "push",
                "primaryMuscle", "chest",
                "equipment", "barbell"));
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(create.getBody());
        assertThat(dto.get("custom").asBoolean()).isTrue();
        assertThat(dto.get("name").asText()).isEqualTo("Wide-Grip Bench");

        // Alice (owner) sees the custom alongside the globals.
        JsonNode aliceList = json(search(aliceToken, "wide", null, 50).getBody());
        assertThat(aliceList.get("items").size()).isEqualTo(1);
        assertThat(aliceList.get("items").get(0).get("custom").asBoolean()).isTrue();

        // Bob (not the owner) does not.
        JsonNode bobList = json(search(bobToken, "wide", null, 50).getBody());
        assertThat(bobList.get("items").size()).isZero();
    }

    @Test
    void create_rejects_duplicate_name_from_same_user_case_insensitive() {
        create(aliceToken, Map.of("name", "My Lift")).getBody();

        ResponseEntity<String> dup = create(aliceToken, Map.of("name", "MY LIFT"));
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(json(dup.getBody()).get("code").asText())
                .isEqualTo(MessageCode.EXERCISE_ALREADY_EXISTS.name());
    }

    @Test
    void create_allows_same_name_for_different_users() {
        assertThat(create(aliceToken, Map.of("name", "Pause Squat")).getStatusCode().value())
                .isEqualTo(201);
        assertThat(create(bobToken, Map.of("name", "Pause Squat")).getStatusCode().value())
                .isEqualTo(201);
    }

    @Test
    void create_allows_naming_a_custom_after_a_global() {
        // "Bench Press" exists as a global; Alice can still fork it as a custom
        // (her notes / equipment may differ). The dedupe rule only fires
        // against her own customs, not against the global catalog.
        ResponseEntity<String> response = create(aliceToken, Map.of("name", "Bench Press"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(json(response.getBody()).get("custom").asBoolean()).isTrue();
    }

    @Test
    void create_returns_400_on_blank_name() {
        ResponseEntity<String> response = create(aliceToken, Map.of("name", "   "));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    // ── helpers ────────────────────────────────────────────────────────────

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

    private ResponseEntity<String> search(String token, String q, Long cursor, int limit) {
        // Use URI templates so DefaultUriBuilderFactory does the param encoding —
        // pre-encoding here would get re-encoded (a literal `%20` becomes `%2520`).
        StringBuilder template = new StringBuilder("/api/exercises?limit={limit}");
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("limit", limit);
        if (q != null) {
            template.append("&q={q}");
            vars.put("q", q);
        }
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

    private ResponseEntity<String> create(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/exercises",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }
}
