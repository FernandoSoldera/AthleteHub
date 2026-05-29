package com.example.athletehub.integration;

import com.example.athletehub.repository.FollowRepository;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-022 (find people + suggestions) and AH-023 (public
 * profile by handle).
 */
class SearchAndProfileIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FollowRepository followRepository;
    @Autowired UserCountersRepository userCountersRepository;

    private long aliceId;
    private long bobId;
    private long caraId;
    private long danId;

    private String aliceToken;

    @BeforeEach
    void registerCohort() {
        refreshTokenRepository.deleteAll();
        followRepository.deleteAll();
        userCountersRepository.deleteAll();
        userRepository.deleteAll();

        aliceId = register("alice@example.com", "Alice Anderson", "alice.lifts");
        bobId   = register("bob@example.com",   "Bob Bennett",    "bob.runs");
        caraId  = register("cara@example.com",  "Cara Carter",    "cara.cycles");
        danId   = register("dan@example.com",   "Dan Doyle",      "dan.lifts");

        aliceToken = json(login("alice@example.com").getBody()).get("accessToken").asText();
    }

    // ── AH-022 — search ───────────────────────────────────────────────────

    @Test
    void search_matches_partial_full_name_case_insensitive_excluding_self() {
        // "ar" appears in "Alice Anderson", "Cara Carter", but Alice is self → excluded.
        JsonNode body = json(get(aliceToken, "/api/users/search?q=ar").getBody());
        Set<String> handles = handleSet(body);
        assertThat(handles).containsExactlyInAnyOrder("cara.cycles");
    }

    @Test
    void search_matches_partial_handle() {
        // ".lifts" hits alice.lifts (self, excluded) and dan.lifts.
        JsonNode body = json(get(aliceToken, "/api/users/search?q=lifts").getBody());
        Set<String> handles = handleSet(body);
        assertThat(handles).containsExactlyInAnyOrder("dan.lifts");
    }

    @Test
    void search_empty_query_returns_empty_page() {
        ResponseEntity<String> response = get(aliceToken, "/api/users/search?q=");
        JsonNode body = json(response.getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void search_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/users/search?q=anything",
                HttpMethod.GET, new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── AH-022 — suggestions + mutuals ────────────────────────────────────

    @Test
    void suggestions_exclude_me_and_people_i_already_follow() {
        // Alice follows bob.
        follow(aliceToken, bobId);

        JsonNode body = json(get(aliceToken, "/api/users/suggestions").getBody());
        Set<String> handles = handleSet(body);
        // Alice (self) and bob (already followed) are excluded → cara + dan remain.
        assertThat(handles).containsExactlyInAnyOrder("cara.cycles", "dan.lifts");
    }

    @Test
    void suggestions_carry_mutual_follow_count() {
        // Alice follows Bob and Cara. Dan also follows Bob and Cara.
        // → for Alice's suggestion list, Dan has 2 mutuals (Bob, Cara).
        follow(aliceToken, bobId);
        follow(aliceToken, caraId);
        String danToken = json(login("dan@example.com").getBody()).get("accessToken").asText();
        follow(danToken, bobId);
        follow(danToken, caraId);

        JsonNode body = json(get(aliceToken, "/api/users/suggestions").getBody());
        JsonNode items = body.get("items");
        // Only Dan is unfollowed by Alice now (bob + cara are followed; alice is self).
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("handle").asText()).isEqualTo("dan.lifts");
        assertThat(items.get(0).get("mutualCount").asLong()).isEqualTo(2L);
    }

    // ── AH-023 — public profile by handle ─────────────────────────────────

    @Test
    void public_profile_by_handle_returns_user_counters_and_i_follow_flag() {
        follow(aliceToken, bobId);

        JsonNode body = json(get(aliceToken, "/api/users/bob.runs").getBody());
        assertThat(body.get("user").get("handle").asText()).isEqualTo("bob.runs");
        assertThat(body.get("user").get("fullName").asText()).isEqualTo("Bob Bennett");
        assertThat(body.get("followers").asInt()).isEqualTo(1); // alice -> bob
        assertThat(body.get("following").asInt()).isZero();
        assertThat(body.get("iFollow").asBoolean()).isTrue();
    }

    @Test
    void public_profile_for_unfollowed_user_has_i_follow_false() {
        JsonNode body = json(get(aliceToken, "/api/users/cara.cycles").getBody());
        assertThat(body.get("iFollow").asBoolean()).isFalse();
    }

    @Test
    void public_profile_for_self_returns_i_follow_false() {
        JsonNode body = json(get(aliceToken, "/api/users/alice.lifts").getBody());
        assertThat(body.get("user").get("handle").asText()).isEqualTo("alice.lifts");
        assertThat(body.get("iFollow").asBoolean()).isFalse();
    }

    @Test
    void public_profile_for_unknown_handle_returns_404() {
        ResponseEntity<String> response = get(aliceToken, "/api/users/nobody.here");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private long register(String email, String fullName, String handle) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", fullName,
                        "handle", handle), jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> login(String email) {
        return rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD), jsonHeaders()),
                String.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> get(String token, String path) {
        return rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class);
    }

    private ResponseEntity<String> follow(String token, long targetId) {
        return rest.exchange(
                "/api/users/" + targetId + "/follow",
                HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private Set<String> handleSet(JsonNode pageBody) {
        Set<String> handles = new HashSet<>();
        pageBody.get("items").forEach(node -> handles.add(node.get("handle").asText()));
        return handles;
    }
}
