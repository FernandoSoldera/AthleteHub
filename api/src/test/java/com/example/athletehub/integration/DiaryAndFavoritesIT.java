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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-053 — POST/DELETE diary entries + GET/POST/DELETE
 * favorites. Covers per-user visibility (no leakage), food-visibility
 * gating on diary + favorites (you can't reference another user's
 * custom), idempotent favorite add/remove, the scaled-macros echo on
 * diary entry create, and bean-validation rejection paths.
 */
class DiaryAndFavoritesIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;
    private long bobId;
    private long chickenId;     // global
    private long bobsCustomId;  // private to Bob

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM diary_entries");
        jdbc.update("DELETE FROM favorites");
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM diet_meals");
        jdbc.update("DELETE FROM diet_plans");
        jdbc.update("DELETE FROM foods WHERE created_by IS NOT NULL");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);

        chickenId = jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND is_global = true",
                Long.class, "Chicken Breast (cooked)");

        // Bob owns a private custom food Alice can't see.
        bobsCustomId = jdbc.queryForObject(
                "INSERT INTO foods (name, is_global, created_by, serving_size_g, " +
                        "kcal, protein_g, carb_g, fat_g) " +
                        "VALUES ('Bob''s Shake', false, ?, 100, 200, 25, 10, 5) RETURNING id",
                Long.class, bobId);
    }

    // ── diary: create ────────────────────────────────────────────────────

    @Test
    void post_diary_returns_201_with_scaled_macros() {
        // 200g chicken (165 kcal / 100 g) → 330 kcal, 62.0 g protein.
        ResponseEntity<String> response = postDiary(aliceToken, Map.of(
                "foodId", chickenId,
                "amount", 200,
                "unit", "g",
                "mealLabel", "Lunch"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("foodName").asText()).isEqualTo("Chicken Breast (cooked)");
        assertThat(dto.get("amount").decimalValue().intValue()).isEqualTo(200);
        assertThat(dto.get("unit").asText()).isEqualTo("g");
        assertThat(dto.get("mealLabel").asText()).isEqualTo("Lunch");
        assertThat(dto.get("source").asText()).isEqualTo("self");
        assertThat(dto.get("macros").get("kcal").decimalValue().doubleValue()).isEqualTo(330.00);
        assertThat(dto.get("macros").get("proteinG").decimalValue().doubleValue()).isEqualTo(62.00);
        assertThat(dto.get("eatenAt").isNull()).isFalse();
    }

    @Test
    void post_diary_defaults_source_to_self_and_eaten_at_to_now() {
        ResponseEntity<String> response = postDiary(aliceToken, Map.of(
                "foodId", chickenId, "amount", 100, "unit", "g"));
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("source").asText()).isEqualTo("self");
        assertThat(dto.get("eatenAt").isNull()).isFalse();
        assertThat(dto.get("mealLabel").isNull()).isTrue();
    }

    @Test
    void post_diary_accepts_plan_and_favorite_sources() {
        for (String source : new String[]{"plan", "favorite"}) {
            Map<String, Object> body = new HashMap<>();
            body.put("foodId", chickenId);
            body.put("amount", 100);
            body.put("unit", "g");
            body.put("source", source);
            ResponseEntity<String> response = postDiary(aliceToken, body);
            assertThat(response.getStatusCode().value())
                    .as("source %s", source).isEqualTo(201);
            assertThat(json(response.getBody()).get("source").asText()).isEqualTo(source);
        }
    }

    @Test
    void post_diary_rejects_coach_source_from_client() {
        Map<String, Object> body = new HashMap<>();
        body.put("foodId", chickenId);
        body.put("amount", 100);
        body.put("unit", "g");
        body.put("source", "coach");
        ResponseEntity<String> response = postDiary(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void post_diary_rejects_unknown_food_with_404() {
        ResponseEntity<String> response = postDiary(aliceToken, Map.of(
                "foodId", 999_999L, "amount", 100, "unit", "g"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.FOOD_NOT_FOUND.name());
    }

    @Test
    void post_diary_rejects_another_users_custom_food_with_404() {
        // Alice can't see Bob's Shake.
        ResponseEntity<String> response = postDiary(aliceToken, Map.of(
                "foodId", bobsCustomId, "amount", 100, "unit", "g"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.FOOD_NOT_FOUND.name());
    }

    @Test
    void post_diary_rejects_invalid_unit_and_zero_amount() {
        ResponseEntity<String> badUnit = postDiary(aliceToken, Map.of(
                "foodId", chickenId, "amount", 100, "unit", "oz"));
        assertThat(badUnit.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> zeroAmount = postDiary(aliceToken, Map.of(
                "foodId", chickenId, "amount", 0, "unit", "g"));
        assertThat(zeroAmount.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void post_diary_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/diary", HttpMethod.POST,
                new HttpEntity<>(Map.of("foodId", chickenId, "amount", 100, "unit", "g"),
                        jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── diary: delete ────────────────────────────────────────────────────

    @Test
    void delete_diary_removes_the_entry() {
        long entryId = postDiaryAndId(aliceToken, Map.of(
                "foodId", chickenId, "amount", 100, "unit", "g"));

        ResponseEntity<String> response = rest.exchange(
                "/api/diet/diary/" + entryId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM diary_entries WHERE id = ?", Integer.class, entryId);
        assertThat(remaining).isZero();
    }

    @Test
    void delete_diary_on_another_users_entry_returns_404() {
        long entryId = postDiaryAndId(aliceToken, Map.of(
                "foodId", chickenId, "amount", 100, "unit", "g"));

        ResponseEntity<String> response = rest.exchange(
                "/api/diet/diary/" + entryId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(bobToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.DIARY_ENTRY_NOT_FOUND.name());
    }

    @Test
    void delete_diary_unknown_returns_404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/diary/999999",
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── favorites ────────────────────────────────────────────────────────

    @Test
    void list_favorites_returns_empty_when_none() {
        JsonNode body = json(listFavorites(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void post_favorite_returns_201_with_hydrated_food() {
        ResponseEntity<String> response = postFavorite(aliceToken, chickenId);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("food").get("id").asLong()).isEqualTo(chickenId);
        assertThat(dto.get("food").get("name").asText()).isEqualTo("Chicken Breast (cooked)");
    }

    @Test
    void post_favorite_is_idempotent_returns_same_row_on_dup() {
        long firstId = json(postFavorite(aliceToken, chickenId).getBody()).get("id").asLong();
        ResponseEntity<String> second = postFavorite(aliceToken, chickenId);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        long secondId = json(second.getBody()).get("id").asLong();
        assertThat(secondId).isEqualTo(firstId);

        // Only one row in the DB.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND food_id = ?",
                Integer.class, aliceId, chickenId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void post_favorite_rejects_another_users_custom_with_404() {
        ResponseEntity<String> response = postFavorite(aliceToken, bobsCustomId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.FOOD_NOT_FOUND.name());
    }

    @Test
    void list_favorites_newest_first_per_user() {
        long oats = jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND is_global = true",
                Long.class, "Oats (dry)");
        long banana = jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND is_global = true",
                Long.class, "Banana");

        postFavorite(aliceToken, oats);
        postFavorite(aliceToken, banana);
        postFavorite(aliceToken, chickenId);

        JsonNode body = json(listFavorites(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(3);
        // Newest first (id DESC) — chicken was the most recently added.
        assertThat(body.get("items").get(0).get("food").get("id").asLong()).isEqualTo(chickenId);
        assertThat(body.get("items").get(1).get("food").get("id").asLong()).isEqualTo(banana);
        assertThat(body.get("items").get(2).get("food").get("id").asLong()).isEqualTo(oats);
    }

    @Test
    void list_favorites_does_not_leak_other_users_favorites() {
        postFavorite(bobToken, chickenId);
        JsonNode body = json(listFavorites(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
    }

    @Test
    void delete_favorite_removes_by_food_id_and_is_idempotent() {
        postFavorite(aliceToken, chickenId);
        Integer beforeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ?",
                Integer.class, aliceId);
        assertThat(beforeCount).isEqualTo(1);

        ResponseEntity<String> first = rest.exchange(
                "/api/diet/favorites/" + chickenId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(first.getStatusCode().value()).isEqualTo(204);

        // Already removed — second delete is still 204 (idempotent).
        ResponseEntity<String> second = rest.exchange(
                "/api/diet/favorites/" + chickenId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)),
                String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(204);

        Integer afterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ?",
                Integer.class, aliceId);
        assertThat(afterCount).isZero();
    }

    @Test
    void delete_favorite_only_touches_callers_row() {
        // Both Alice and Bob favorite chicken.
        postFavorite(aliceToken, chickenId);
        postFavorite(bobToken, chickenId);

        // Bob removes his — Alice's stays.
        rest.exchange(
                "/api/diet/favorites/" + chickenId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(bobToken)),
                String.class);

        Integer alice = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND food_id = ?",
                Integer.class, aliceId, chickenId);
        Integer bob = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND food_id = ?",
                Integer.class, bobId, chickenId);
        assertThat(alice).isEqualTo(1);
        assertThat(bob).isZero();
    }

    @Test
    void favorite_food_delete_cascades_favorite_row() {
        // Alice favorites Bob's custom… wait, she can't — visibility blocks it.
        // Test the cascade with her own custom instead.
        long aliceCustom = jdbc.queryForObject(
                "INSERT INTO foods (name, is_global, created_by, serving_size_g, " +
                        "kcal, protein_g, carb_g, fat_g) " +
                        "VALUES ('Alice''s Shake', false, ?, 100, 180, 22, 8, 4) RETURNING id",
                Long.class, aliceId);
        postFavorite(aliceToken, aliceCustom);

        jdbc.update("DELETE FROM foods WHERE id = ?", aliceCustom);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE food_id = ?",
                Integer.class, aliceCustom);
        assertThat(remaining).isZero();
    }

    @Test
    void favorite_endpoints_without_token_return_401() {
        ResponseEntity<String> list = rest.exchange(
                "/api/diet/favorites", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(list.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> post = rest.exchange(
                "/api/diet/favorites", HttpMethod.POST,
                new HttpEntity<>(Map.of("foodId", chickenId), jsonHeaders()), String.class);
        assertThat(post.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> delete = rest.exchange(
                "/api/diet/favorites/" + chickenId, HttpMethod.DELETE,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(delete.getStatusCode().value()).isEqualTo(401);
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

    private ResponseEntity<String> postDiary(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/diet/diary", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);
    }

    private long postDiaryAndId(String token, Map<String, Object> body) {
        ResponseEntity<String> response = postDiary(token, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> postFavorite(String token, long foodId) {
        return rest.exchange(
                "/api/diet/favorites", HttpMethod.POST,
                new HttpEntity<>(Map.of("foodId", foodId), bearer(token)), String.class);
    }

    private ResponseEntity<String> listFavorites(String token, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/diet/favorites?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("limit", limit);
        if (cursor != null) {
            template.append("&cursor={cursor}");
            vars.put("cursor", cursor);
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }
}
