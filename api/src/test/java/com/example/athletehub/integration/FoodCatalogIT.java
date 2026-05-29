package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.FoodRepository;
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
 * End-to-end test of AH-051 — the food catalog. Exercises:
 *
 * <ul>
 *   <li>the seed migration loaded the global catalog (~27 staples),</li>
 *   <li>the search endpoint matches case-insensitively on substring,</li>
 *   <li>cursor pagination is stable and signals "more pages" correctly,</li>
 *   <li>a custom food is visible to its owner but not to other users,</li>
 *   <li>the create endpoint stamps {@code is_global = false / created_by =
 *       caller}, rejects duplicate names from the same caller, and accepts
 *       a global-name "fork" because that's intentional,</li>
 *   <li>bean validation rejects bad macro values + missing required fields.</li>
 * </ul>
 */
class FoodCatalogIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FoodRepository foodRepository;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        // Drop only user-created customs; keep the seed catalog intact.
        foodRepository.findAll().stream()
                .filter(f -> f.getCreatedBy() != null)
                .forEach(foodRepository::delete);
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
        // The seed inserts ~27 rows; assert "at least 25" so the test
        // doesn't turn red the next time someone tunes the list by ±a few.
        assertThat(body.get("items").size()).isGreaterThanOrEqualTo(25);
        // Every returned row is global for a user with no customs.
        body.get("items").forEach(item ->
                assertThat(item.get("custom").asBoolean()).isFalse());
    }

    @Test
    void search_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/foods",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── search ────────────────────────────────────────────────────────────

    @Test
    void search_matches_case_insensitive_substring() {
        // "Chicken Breast (cooked)" only.
        JsonNode body = json(search(aliceToken, "chick", null, 100).getBody());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("name").asText().toLowerCase())
                .contains("chick");
    }

    @Test
    void blank_query_returns_full_catalog() {
        JsonNode body = json(search(aliceToken, "   ", null, 100).getBody());
        assertThat(body.get("items").size()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void pagination_uses_cursor_to_walk_all_pages() {
        JsonNode page1 = json(search(aliceToken, null, null, 10).getBody());
        assertThat(page1.get("items").size()).isEqualTo(10);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotNull();
        long lastId = page1.get("items").get(9).get("id").asLong();

        JsonNode page2 = json(search(aliceToken, null, Long.parseLong(cursor), 10).getBody());
        assertThat(page2.get("items").size()).isEqualTo(10);
        assertThat(page2.get("items").get(0).get("id").asLong()).isGreaterThan(lastId);
    }

    // ── custom create + visibility ────────────────────────────────────────

    @Test
    void create_marks_custom_and_owner_can_see_it_but_other_users_cannot() {
        ResponseEntity<String> create = create(aliceToken, Map.of(
                "name", "Home-blended Smoothie",
                "brand", "Kitchen",
                "servingSizeG", 250,
                "kcal", 320,
                "proteinG", 25,
                "carbG", 30,
                "fatG", 8));
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(create.getBody());
        assertThat(dto.get("custom").asBoolean()).isTrue();
        assertThat(dto.get("name").asText()).isEqualTo("Home-blended Smoothie");
        assertThat(dto.get("brand").asText()).isEqualTo("Kitchen");
        assertThat(dto.get("kcal").decimalValue().intValue()).isEqualTo(320);

        JsonNode aliceList = json(search(aliceToken, "smoothie", null, 50).getBody());
        assertThat(aliceList.get("items").size()).isEqualTo(1);
        assertThat(aliceList.get("items").get(0).get("custom").asBoolean()).isTrue();

        JsonNode bobList = json(search(bobToken, "smoothie", null, 50).getBody());
        assertThat(bobList.get("items").size()).isZero();
    }

    @Test
    void create_rejects_duplicate_name_from_same_user_case_insensitive() {
        create(aliceToken, minimalFood("My Shake")).getBody();

        ResponseEntity<String> dup = create(aliceToken, minimalFood("MY SHAKE"));
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(json(dup.getBody()).get("code").asText())
                .isEqualTo(MessageCode.FOOD_ALREADY_EXISTS.name());
    }

    @Test
    void create_allows_same_name_for_different_users() {
        assertThat(create(aliceToken, minimalFood("Post-WO Shake"))
                .getStatusCode().value()).isEqualTo(201);
        assertThat(create(bobToken, minimalFood("Post-WO Shake"))
                .getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void create_allows_naming_a_custom_after_a_global() {
        // "Banana" exists as a global; Alice can still fork it.
        ResponseEntity<String> response = create(aliceToken, minimalFood("Banana"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(json(response.getBody()).get("custom").asBoolean()).isTrue();
    }

    @Test
    void create_returns_400_on_negative_macro() {
        Map<String, Object> body = minimalFood("BadFood");
        body.put("proteinG", -5);
        ResponseEntity<String> response = create(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void create_returns_400_on_zero_serving_size() {
        Map<String, Object> body = minimalFood("ZeroServing");
        body.put("servingSizeG", 0);
        ResponseEntity<String> response = create(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_returns_400_on_blank_name() {
        Map<String, Object> body = minimalFood("   ");
        ResponseEntity<String> response = create(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
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
        StringBuilder template = new StringBuilder("/api/foods?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
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
                "/api/foods",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private Map<String, Object> minimalFood(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("servingSizeG", 100);
        body.put("kcal", 200);
        body.put("proteinG", 20);
        body.put("carbG", 10);
        body.put("fatG", 5);
        return body;
    }
}
