package com.example.athletehub.integration;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-062 — GET /api/feed (home timeline) + GET
 * /api/users/{handle}/posts (profile feed). Covers the full visibility
 * matrix (self / follower / stranger × public / followers / private),
 * type-filter parsing, soft-delete exclusion, author + iLiked hydration,
 * and cursor pagination.
 *
 * <p>Posts and follow edges are seeded via {@link JdbcTemplate} so the
 * test can place exact visibility values without going through the
 * publish path. The PostsIT covers the publish path; this IT covers
 * the read path.
 */
class FeedTimelineIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";    // followed by Alice
    private static final String CARA_EMAIL = "cara@example.com";  // not followed
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private String caraToken;
    private long aliceId;
    private long bobId;
    private long caraId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM post_comments");
        jdbc.update("DELETE FROM post_likes");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM follows");
        // user_counters is FK-cleaned by the user delete cascade.
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        register(CARA_EMAIL, "Cara C.", "cara.cycles");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        caraId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, CARA_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);
        caraToken = authToken(CARA_EMAIL);

        // Alice follows Bob; Cara is unfollowed by both.
        jdbc.update(
                "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)",
                aliceId, bobId);
    }

    // ── home feed: visibility matrix ─────────────────────────────────────

    @Test
    void home_feed_empty_when_no_posts() {
        JsonNode body = json(homeFeed(aliceToken, null, 20, null).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void home_feed_includes_own_posts_all_visibilities() {
        long pub = insertPost(aliceId, "manual", "public");
        long fol = insertPost(aliceId, "manual", "followers");
        long pri = insertPost(aliceId, "manual", "private");

        Set<Long> ids = idsOf(homeFeed(aliceToken, null, 50, null));
        assertThat(ids).contains(pub, fol, pri);
    }

    @Test
    void home_feed_includes_followees_public_and_followers_excludes_private() {
        long bobPub = insertPost(bobId, "manual", "public");
        long bobFol = insertPost(bobId, "manual", "followers");
        long bobPri = insertPost(bobId, "manual", "private");

        Set<Long> ids = idsOf(homeFeed(aliceToken, null, 50, null));
        assertThat(ids).contains(bobPub, bobFol);
        assertThat(ids).doesNotContain(bobPri);
    }

    @Test
    void home_feed_excludes_non_followed_users_posts_even_public() {
        long caraPub = insertPost(caraId, "manual", "public");
        long caraFol = insertPost(caraId, "manual", "followers");

        Set<Long> ids = idsOf(homeFeed(aliceToken, null, 50, null));
        assertThat(ids).doesNotContain(caraPub, caraFol);
    }

    @Test
    void home_feed_excludes_soft_deleted_posts() {
        long alivePost = insertPost(bobId, "manual", "public");
        long deletedPost = insertPost(bobId, "manual", "public");
        jdbc.update("UPDATE posts SET deleted_at = NOW() WHERE id = ?", deletedPost);

        Set<Long> ids = idsOf(homeFeed(aliceToken, null, 50, null));
        assertThat(ids).contains(alivePost);
        assertThat(ids).doesNotContain(deletedPost);
    }

    @Test
    void home_feed_orders_newest_first_and_paginates_with_cursor() {
        long p1 = insertPost(aliceId, "manual", "public");
        long p2 = insertPost(aliceId, "manual", "public");
        long p3 = insertPost(aliceId, "manual", "public");

        JsonNode page1 = json(homeFeed(aliceToken, null, 2, null).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("post").get("id").asLong()).isEqualTo(p3);
        assertThat(page1.get("items").get(1).get("post").get("id").asLong()).isEqualTo(p2);
        long cursor = Long.parseLong(page1.get("nextCursor").asText());

        JsonNode page2 = json(homeFeed(aliceToken, cursor, 2, null).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("post").get("id").asLong()).isEqualTo(p1);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    // ── home feed: type filter ──────────────────────────────────────────

    @Test
    void home_feed_type_filter_narrows_results() {
        long aliceWorkout = insertPost(aliceId, "workout", "public");
        long aliceManual = insertPost(aliceId, "manual", "public");
        long bobRun = insertPost(bobId, "run", "public");

        // single type
        Set<Long> workoutOnly = idsOf(homeFeed(aliceToken, null, 50, "workout"));
        assertThat(workoutOnly).containsOnly(aliceWorkout);

        // multiple types (csv)
        Set<Long> workoutOrRun = idsOf(homeFeed(aliceToken, null, 50, "workout,run"));
        assertThat(workoutOrRun).containsOnly(aliceWorkout, bobRun);

        // unknown type silently dropped — falls back to unfiltered
        Set<Long> allWhenUnknown = idsOf(homeFeed(aliceToken, null, 50, "swim"));
        assertThat(allWhenUnknown).contains(aliceWorkout, aliceManual, bobRun);
    }

    // ── home feed: hydration ────────────────────────────────────────────

    @Test
    void home_feed_hydrates_author_and_i_liked() {
        long postId = insertPost(bobId, "manual", "public");
        // Alice likes it.
        jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)", postId, aliceId);

        JsonNode body = json(homeFeed(aliceToken, null, 20, null).getBody());
        assertThat(body.get("items").size()).isEqualTo(1);
        JsonNode item = body.get("items").get(0);
        assertThat(item.get("author").get("id").asLong()).isEqualTo(bobId);
        assertThat(item.get("author").get("handle").asText()).isEqualTo("bob.runs");
        assertThat(item.get("iLiked").asBoolean()).isTrue();
    }

    @Test
    void home_feed_i_liked_false_when_viewer_has_not_liked() {
        long postId = insertPost(bobId, "manual", "public");
        // Cara likes it; Alice hasn't.
        jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)", postId, caraId);

        JsonNode body = json(homeFeed(aliceToken, null, 20, null).getBody());
        assertThat(body.get("items").get(0).get("iLiked").asBoolean()).isFalse();
    }

    @Test
    void home_feed_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/feed", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── profile feed: visibility matrix ─────────────────────────────────

    @Test
    void profile_feed_self_view_sees_all_three_visibilities() {
        long pub = insertPost(aliceId, "manual", "public");
        long fol = insertPost(aliceId, "manual", "followers");
        long pri = insertPost(aliceId, "manual", "private");

        Set<Long> ids = idsOf(profileFeed(aliceToken, "alice.lifts"));
        assertThat(ids).contains(pub, fol, pri);
    }

    @Test
    void profile_feed_follower_view_sees_public_and_followers_not_private() {
        // Alice viewing Bob; she follows him.
        long pub = insertPost(bobId, "manual", "public");
        long fol = insertPost(bobId, "manual", "followers");
        long pri = insertPost(bobId, "manual", "private");

        Set<Long> ids = idsOf(profileFeed(aliceToken, "bob.runs"));
        assertThat(ids).contains(pub, fol);
        assertThat(ids).doesNotContain(pri);
    }

    @Test
    void profile_feed_stranger_view_sees_only_public() {
        // Cara doesn't follow Bob.
        long pub = insertPost(bobId, "manual", "public");
        long fol = insertPost(bobId, "manual", "followers");
        long pri = insertPost(bobId, "manual", "private");

        Set<Long> ids = idsOf(profileFeed(caraToken, "bob.runs"));
        assertThat(ids).contains(pub);
        assertThat(ids).doesNotContain(fol, pri);
    }

    @Test
    void profile_feed_excludes_soft_deleted() {
        long alive = insertPost(bobId, "manual", "public");
        long dead = insertPost(bobId, "manual", "public");
        jdbc.update("UPDATE posts SET deleted_at = NOW() WHERE id = ?", dead);

        Set<Long> ids = idsOf(profileFeed(aliceToken, "bob.runs"));
        assertThat(ids).contains(alive);
        assertThat(ids).doesNotContain(dead);
    }

    @Test
    void profile_feed_unknown_handle_returns_404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/users/no.such.user/posts", HttpMethod.GET,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void profile_feed_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/users/bob.runs/posts", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void profile_feed_paginates_with_cursor() {
        long p1 = insertPost(bobId, "manual", "public");
        long p2 = insertPost(bobId, "manual", "public");
        long p3 = insertPost(bobId, "manual", "public");

        JsonNode page1 = json(profileFeedPaged(aliceToken, "bob.runs", null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("post").get("id").asLong()).isEqualTo(p3);
        long cursor = Long.parseLong(page1.get("nextCursor").asText());

        JsonNode page2 = json(profileFeedPaged(aliceToken, "bob.runs", cursor, 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("post").get("id").asLong()).isEqualTo(p1);
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

    private long insertPost(long authorId, String type, String visibility) {
        return jdbc.queryForObject(
                "INSERT INTO posts (author_id, type, visibility) VALUES (?, ?, ?) RETURNING id",
                Long.class, authorId, type, visibility);
    }

    private ResponseEntity<String> homeFeed(String token, Long cursor, int limit, String type) {
        StringBuilder template = new StringBuilder("/api/feed?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("limit", limit);
        if (cursor != null) {
            template.append("&cursor={cursor}");
            vars.put("cursor", cursor);
        }
        if (type != null) {
            template.append("&type={type}");
            vars.put("type", type);
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }

    private ResponseEntity<String> profileFeed(String token, String handle) {
        return profileFeedPaged(token, handle, null, 50);
    }

    private ResponseEntity<String> profileFeedPaged(String token, String handle, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/users/{handle}/posts?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("handle", handle);
        vars.put("limit", limit);
        if (cursor != null) {
            template.append("&cursor={cursor}");
            vars.put("cursor", cursor);
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }

    private Set<Long> idsOf(ResponseEntity<String> response) {
        Set<Long> out = new HashSet<>();
        JsonNode items = json(response.getBody()).get("items");
        for (JsonNode item : items) {
            out.add(item.get("post").get("id").asLong());
        }
        return out;
    }
}
