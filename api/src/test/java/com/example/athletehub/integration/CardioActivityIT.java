package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.CardioActivityRepository;
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
 * End-to-end test of AH-034 — cardio create + recent list.
 *
 * <p>Covers the happy create path with full and minimal payloads, bean
 * validation rejection for bad type / out-of-range HR / negative
 * distance, per-user visibility on the recent list, and cursor-paginated
 * walk with the standard limit + 1 has-more signal.
 */
class CardioActivityIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CardioActivityRepository cardioRepository;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        cardioRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
    }

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void create_persists_full_payload_and_returns_dto() {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "run");
        body.put("distanceM", 5000);
        body.put("durationSeconds", 1800);
        body.put("avgPaceSPerKm", 360);
        body.put("avgPowerW", 220);
        body.put("avgHr", 152);
        body.put("maxHr", 178);
        body.put("elevationGainM", 35);
        body.put("kcal", 450);
        body.put("notes", "Sunday long run");

        ResponseEntity<String> response = create(aliceToken, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("id").asLong()).isPositive();
        assertThat(dto.get("type").asText()).isEqualTo("run");
        assertThat(dto.get("distanceM").decimalValue().intValue()).isEqualTo(5000);
        assertThat(dto.get("durationSeconds").asInt()).isEqualTo(1800);
        assertThat(dto.get("avgHr").asInt()).isEqualTo(152);
        assertThat(dto.get("notes").asText()).isEqualTo("Sunday long run");
        assertThat(dto.get("source").asText()).isEqualTo("self");
        assertThat(dto.get("startedAt").isNull()).isFalse();
    }

    @Test
    void create_accepts_minimal_payload() {
        // Only the required fields — server fills startedAt and source.
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "type", "walk",
                "distanceM", 1500,
                "durationSeconds", 900));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("type").asText()).isEqualTo("walk");
        assertThat(dto.get("avgHr").isNull()).isTrue();
        assertThat(dto.get("notes").isNull()).isTrue();
        assertThat(dto.get("source").asText()).isEqualTo("self");
        assertThat(dto.get("startedAt").isNull()).isFalse();
    }

    @Test
    void create_rejects_unknown_type() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "type", "swim",
                "distanceM", 1500,
                "durationSeconds", 1200));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void create_rejects_out_of_range_hr() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "type", "run",
                "distanceM", 5000,
                "durationSeconds", 1800,
                "avgHr", 350)); // > 299
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_rejects_negative_distance() {
        ResponseEntity<String> response = create(aliceToken, Map.of(
                "type", "run",
                "distanceM", -100,
                "durationSeconds", 1800));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/cardio-activities",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "run",
                        "distanceM", 5000,
                        "durationSeconds", 1800), jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── list recent ──────────────────────────────────────────────────────

    @Test
    void list_returns_empty_page_when_no_activities() {
        JsonNode body = json(list(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_returns_caller_activities_newest_first() {
        long id1 = createAndId(aliceToken, "run", 5000, 1800);
        long id2 = createAndId(aliceToken, "walk", 1500, 900);
        long id3 = createAndId(aliceToken, "cycle", 20000, 3600);

        JsonNode body = json(list(aliceToken, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(3);
        // Newest first (id DESC).
        assertThat(body.get("items").get(0).get("id").asLong()).isEqualTo(id3);
        assertThat(body.get("items").get(1).get("id").asLong()).isEqualTo(id2);
        assertThat(body.get("items").get(2).get("id").asLong()).isEqualTo(id1);
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_does_not_leak_other_users_activities() {
        createAndId(aliceToken, "run", 5000, 1800);
        createAndId(bobToken, "run", 6000, 2000);

        JsonNode alice = json(list(aliceToken, null, 20).getBody());
        assertThat(alice.get("items").size()).isEqualTo(1);
        assertThat(alice.get("items").get(0).get("distanceM").decimalValue().intValue()).isEqualTo(5000);

        JsonNode bob = json(list(bobToken, null, 20).getBody());
        assertThat(bob.get("items").size()).isEqualTo(1);
        assertThat(bob.get("items").get(0).get("distanceM").decimalValue().intValue()).isEqualTo(6000);
    }

    @Test
    void list_pagination_walks_with_cursor() {
        long id1 = createAndId(aliceToken, "run", 5000, 1800);
        long id2 = createAndId(aliceToken, "walk", 1500, 900);
        long id3 = createAndId(aliceToken, "cycle", 20000, 3600);

        JsonNode page1 = json(list(aliceToken, null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(id3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(id2);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotNull();

        JsonNode page2 = json(list(aliceToken, Long.parseLong(cursor), 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(id1);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void list_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/cardio-activities",
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

    private ResponseEntity<String> create(String token, Map<String, Object> body) {
        return rest.exchange(
                "/api/cardio-activities",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> list(String token, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/cardio-activities?limit={limit}");
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

    private long createAndId(String token, String type, int distanceM, int durationSeconds) {
        ResponseEntity<String> response = create(token, Map.of(
                "type", type,
                "distanceM", distanceM,
                "durationSeconds", durationSeconds));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }
}
