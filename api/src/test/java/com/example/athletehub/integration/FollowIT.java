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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-020 + AH-021: follow / unfollow + the followers /
 * following list reads, with denormalized user_counters staying in sync.
 */
class FollowIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String CARA_EMAIL = "cara@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FollowRepository followRepository;
    @Autowired UserCountersRepository userCountersRepository;

    private String aliceToken;
    private String bobToken;
    private long aliceId;
    private long bobId;
    private long caraId;

    @BeforeEach
    void registerAndLogin() {
        refreshTokenRepository.deleteAll();
        followRepository.deleteAll();
        userCountersRepository.deleteAll();
        userRepository.deleteAll();

        aliceId = register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        bobId = register(BOB_EMAIL, "Bob B.", "bob.runs");
        caraId = register(CARA_EMAIL, "Cara C.", "cara.cycles");

        aliceToken = json(login(ALICE_EMAIL).getBody()).get("accessToken").asText();
        bobToken = json(login(BOB_EMAIL).getBody()).get("accessToken").asText();
    }

    // ── follow / unfollow ──────────────────────────────────────────────────

    @Test
    void follow_then_unfollow_keeps_counters_consistent() {
        assertThat(follow(aliceToken, bobId).getStatusCode().value()).isEqualTo(204);

        // Alice is following Bob; Bob has 1 follower; Alice follows 1 person.
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowing()).isEqualTo(1);
        assertThat(userCountersRepository.findById(bobId).orElseThrow().getFollowers()).isEqualTo(1);
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowers()).isZero();
        assertThat(userCountersRepository.findById(bobId).orElseThrow().getFollowing()).isZero();

        // Idempotent re-follow: still 204, counters unchanged.
        assertThat(follow(aliceToken, bobId).getStatusCode().value()).isEqualTo(204);
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowing()).isEqualTo(1);
        assertThat(userCountersRepository.findById(bobId).orElseThrow().getFollowers()).isEqualTo(1);

        // Unfollow.
        assertThat(unfollow(aliceToken, bobId).getStatusCode().value()).isEqualTo(204);
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowing()).isZero();
        assertThat(userCountersRepository.findById(bobId).orElseThrow().getFollowers()).isZero();

        // Idempotent re-unfollow: still 204, counters stay at zero (no underflow).
        assertThat(unfollow(aliceToken, bobId).getStatusCode().value()).isEqualTo(204);
        assertThat(userCountersRepository.findById(bobId).orElseThrow().getFollowers()).isZero();
    }

    @Test
    void cannot_follow_yourself() {
        assertThat(follow(aliceToken, aliceId).getStatusCode().value()).isEqualTo(400);
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowing()).isZero();
        assertThat(userCountersRepository.findById(aliceId).orElseThrow().getFollowers()).isZero();
    }

    @Test
    void cannot_follow_unknown_user() {
        assertThat(follow(aliceToken, 999_999L).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void follow_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/users/" + bobId + "/follow",
                HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── followers / following list ────────────────────────────────────────

    @Test
    void followers_and_following_lists_reflect_the_graph() {
        // alice -> bob; cara -> bob; alice -> cara
        follow(aliceToken, bobId);
        follow(authToken(CARA_EMAIL), bobId);
        follow(aliceToken, caraId);

        // Alice follows 2 (bob, cara); has 0 followers.
        JsonNode aliceFollowing = json(myFollowing(aliceToken).getBody());
        assertThat(aliceFollowing.get("items").size()).isEqualTo(2);
        assertThat(aliceFollowing.get("nextCursor").isNull()).isTrue();

        // Bob has 2 followers (alice, cara); follows 0.
        JsonNode bobFollowers = json(myFollowers(bobToken).getBody());
        assertThat(bobFollowers.get("items").size()).isEqualTo(2);
        // Most recent first (cursor pagination is id DESC).
        assertThat(bobFollowers.get("items").get(0).get("handle").asText()).isEqualTo("cara.cycles");
        assertThat(bobFollowers.get("items").get(1).get("handle").asText()).isEqualTo("alice.lifts");
    }

    @Test
    void followers_pagination_uses_cursor() {
        // 3 followers of Bob: alice, cara, ... (we need a fourth, so add another)
        follow(aliceToken, bobId);
        follow(authToken(CARA_EMAIL), bobId);

        // Page 1, limit 1
        ResponseEntity<String> page1 = rest.exchange(
                "/api/me/followers?limit=1",
                HttpMethod.GET, new HttpEntity<>(null, bearer(bobToken)),
                String.class);
        JsonNode page1Body = json(page1.getBody());
        assertThat(page1Body.get("items").size()).isEqualTo(1);
        String nextCursor = page1Body.get("nextCursor").asText();
        assertThat(nextCursor).isNotNull();

        // Page 2 using the cursor → next follower, no more pages.
        ResponseEntity<String> page2 = rest.exchange(
                "/api/me/followers?limit=1&cursor=" + nextCursor,
                HttpMethod.GET, new HttpEntity<>(null, bearer(bobToken)),
                String.class);
        JsonNode page2Body = json(page2.getBody());
        assertThat(page2Body.get("items").size()).isEqualTo(1);
        assertThat(page2Body.get("nextCursor").isNull()).isTrue();

        // The two pages return distinct handles.
        assertThat(page1Body.get("items").get(0).get("handle").asText())
                .isNotEqualTo(page2Body.get("items").get(0).get("handle").asText());
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

    private String authToken(String email) {
        return json(login(email).getBody()).get("accessToken").asText();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> follow(String token, long targetId) {
        return rest.exchange(
                "/api/users/" + targetId + "/follow",
                HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> unfollow(String token, long targetId) {
        return rest.exchange(
                "/api/users/" + targetId + "/follow",
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> myFollowers(String token) {
        return rest.exchange(
                "/api/me/followers",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }

    private ResponseEntity<String> myFollowing(String token) {
        return rest.exchange(
                "/api/me/following",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                String.class);
    }
}
