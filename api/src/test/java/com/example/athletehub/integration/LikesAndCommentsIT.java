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
 * End-to-end test of AH-063 — likes + comments. Covers idempotency on
 * both like + unlike, counter math, the visibility gate
 * (own / public / followers / private × self / follower / stranger),
 * comment add + soft-delete + chronological thread + author hydration,
 * and the 401 / 400 / 404 paths.
 */
class LikesAndCommentsIT extends AbstractIntegrationTest {

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
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)",
                aliceId, bobId);
    }

    // ── likes: happy path + counter ──────────────────────────────────────

    @Test
    void like_creates_row_and_bumps_counter() {
        long postId = insertPost(bobId, "manual", "public");

        ResponseEntity<String> response = like(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?",
                Integer.class, postId, aliceId);
        assertThat(rowCount).isEqualTo(1);
        assertThat(likeCount(postId)).isEqualTo(1);
    }

    @Test
    void like_is_idempotent_no_extra_row_or_counter_bump() {
        long postId = insertPost(bobId, "manual", "public");
        like(aliceToken, postId);
        like(aliceToken, postId);

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?",
                Integer.class, postId, aliceId);
        assertThat(rowCount).isEqualTo(1);
        assertThat(likeCount(postId)).isEqualTo(1);
    }

    @Test
    void unlike_removes_row_and_decrements_counter() {
        long postId = insertPost(bobId, "manual", "public");
        like(aliceToken, postId);
        assertThat(likeCount(postId)).isEqualTo(1);

        ResponseEntity<String> response = unlike(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(likeCount(postId)).isZero();
    }

    @Test
    void unlike_is_idempotent_when_never_liked() {
        long postId = insertPost(bobId, "manual", "public");

        ResponseEntity<String> response = unlike(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(likeCount(postId)).isZero();
    }

    @Test
    void unlike_still_works_after_visibility_flipped_to_private() {
        long postId = insertPost(bobId, "manual", "public");
        like(aliceToken, postId);
        jdbc.update("UPDATE posts SET visibility = 'private' WHERE id = ?", postId);

        // Alice can no longer view the post, but should still be able to
        // unlike it (her own row).
        ResponseEntity<String> response = unlike(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(likeCount(postId)).isZero();
    }

    // ── likes: visibility matrix ─────────────────────────────────────────

    @Test
    void like_on_followers_post_by_follower_ok() {
        long postId = insertPost(bobId, "manual", "followers");
        ResponseEntity<String> response = like(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void like_on_followers_post_by_stranger_returns_404() {
        long postId = insertPost(bobId, "manual", "followers");
        ResponseEntity<String> response = like(caraToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.POST_NOT_FOUND.name());
    }

    @Test
    void like_on_private_post_by_other_returns_404() {
        long postId = insertPost(bobId, "manual", "private");
        ResponseEntity<String> response = like(aliceToken, postId);  // Alice follows Bob
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void like_on_own_private_post_ok() {
        long postId = insertPost(aliceId, "manual", "private");
        ResponseEntity<String> response = like(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void like_on_deleted_post_returns_404() {
        long postId = insertPost(bobId, "manual", "public");
        jdbc.update("UPDATE posts SET deleted_at = NOW() WHERE id = ?", postId);
        ResponseEntity<String> response = like(aliceToken, postId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void like_unknown_post_returns_404() {
        ResponseEntity<String> response = like(aliceToken, 999_999L);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void like_without_token_returns_401() {
        long postId = insertPost(bobId, "manual", "public");
        ResponseEntity<String> response = rest.exchange(
                "/api/posts/" + postId + "/likes", HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── comments: happy path + counter ───────────────────────────────────

    @Test
    void add_comment_returns_201_with_hydrated_author() {
        long postId = insertPost(bobId, "manual", "public");

        ResponseEntity<String> response = addComment(aliceToken, postId,
                Map.of("body", "Nice lift!"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("postId").asLong()).isEqualTo(postId);
        assertThat(dto.get("body").asText()).isEqualTo("Nice lift!");
        assertThat(dto.get("author").get("id").asLong()).isEqualTo(aliceId);
        assertThat(dto.get("author").get("handle").asText()).isEqualTo("alice.lifts");

        assertThat(commentCount(postId)).isEqualTo(1);
    }

    @Test
    void add_comment_rejects_empty_body() {
        long postId = insertPost(bobId, "manual", "public");
        ResponseEntity<String> response = addComment(aliceToken, postId,
                Map.of("body", "   "));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void add_comment_respects_visibility_gate() {
        long postId = insertPost(bobId, "manual", "followers");
        // Cara doesn't follow Bob.
        ResponseEntity<String> response = addComment(caraToken, postId,
                Map.of("body", "Hi!"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void delete_comment_soft_deletes_and_decrements_counter() {
        long postId = insertPost(bobId, "manual", "public");
        long commentId = json(addComment(aliceToken, postId, Map.of("body", "x"))
                .getBody()).get("id").asLong();
        assertThat(commentCount(postId)).isEqualTo(1);

        ResponseEntity<String> response = rest.exchange(
                "/api/comments/" + commentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        // Row exists with deleted_at set, but counter is back to zero.
        java.sql.Timestamp deletedAt = jdbc.queryForObject(
                "SELECT deleted_at FROM post_comments WHERE id = ?",
                java.sql.Timestamp.class, commentId);
        assertThat(deletedAt).isNotNull();
        assertThat(commentCount(postId)).isZero();
    }

    @Test
    void delete_comment_twice_returns_404_on_second() {
        long postId = insertPost(bobId, "manual", "public");
        long commentId = json(addComment(aliceToken, postId, Map.of("body", "x"))
                .getBody()).get("id").asLong();
        deleteComment(aliceToken, commentId);

        ResponseEntity<String> second = rest.exchange(
                "/api/comments/" + commentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(404);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.COMMENT_NOT_FOUND.name());
    }

    @Test
    void delete_comment_by_non_author_returns_404() {
        long postId = insertPost(bobId, "manual", "public");
        long commentId = json(addComment(aliceToken, postId, Map.of("body", "x"))
                .getBody()).get("id").asLong();

        // Bob (post author) is not the comment author and can't delete it.
        ResponseEntity<String> response = rest.exchange(
                "/api/comments/" + commentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(bobToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void list_comments_returns_chronological_thread_with_authors() {
        long postId = insertPost(bobId, "manual", "public");
        addComment(aliceToken, postId, Map.of("body", "first"));
        addComment(bobToken, postId, Map.of("body", "second"));
        addComment(aliceToken, postId, Map.of("body", "third"));

        JsonNode body = json(listComments(aliceToken, postId, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(3);
        // Oldest first (id ASC).
        assertThat(body.get("items").get(0).get("body").asText()).isEqualTo("first");
        assertThat(body.get("items").get(1).get("body").asText()).isEqualTo("second");
        assertThat(body.get("items").get(2).get("body").asText()).isEqualTo("third");
        // Author hydrated.
        assertThat(body.get("items").get(0).get("author").get("handle").asText())
                .isEqualTo("alice.lifts");
        assertThat(body.get("items").get(1).get("author").get("handle").asText())
                .isEqualTo("bob.runs");
    }

    @Test
    void list_comments_excludes_soft_deleted() {
        long postId = insertPost(bobId, "manual", "public");
        long aliceComment = json(addComment(aliceToken, postId, Map.of("body", "first")).getBody())
                .get("id").asLong();
        addComment(bobToken, postId, Map.of("body", "second"));
        deleteComment(aliceToken, aliceComment);

        JsonNode body = json(listComments(aliceToken, postId, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("body").asText()).isEqualTo("second");
    }

    @Test
    void list_comments_respects_visibility_gate() {
        long postId = insertPost(bobId, "manual", "followers");
        addComment(aliceToken, postId, Map.of("body", "hi"));  // Alice follows Bob

        // Cara doesn't follow Bob — even the thread read is gated.
        ResponseEntity<String> response = listComments(caraToken, postId, null, 20);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void list_comments_pagination_walks_with_cursor() {
        long postId = insertPost(bobId, "manual", "public");
        long c1 = json(addComment(aliceToken, postId, Map.of("body", "1")).getBody())
                .get("id").asLong();
        long c2 = json(addComment(aliceToken, postId, Map.of("body", "2")).getBody())
                .get("id").asLong();
        long c3 = json(addComment(aliceToken, postId, Map.of("body", "3")).getBody())
                .get("id").asLong();

        JsonNode page1 = json(listComments(aliceToken, postId, null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(c1);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(c2);
        long cursor = Long.parseLong(page1.get("nextCursor").asText());

        JsonNode page2 = json(listComments(aliceToken, postId, cursor, 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(c3);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void comment_endpoints_without_token_return_401() {
        long postId = insertPost(bobId, "manual", "public");
        ResponseEntity<String> add = rest.exchange(
                "/api/posts/" + postId + "/comments", HttpMethod.POST,
                new HttpEntity<>(Map.of("body", "x"), jsonHeaders()), String.class);
        assertThat(add.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> list = rest.exchange(
                "/api/posts/" + postId + "/comments", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(list.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> del = rest.exchange(
                "/api/comments/1", HttpMethod.DELETE,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(del.getStatusCode().value()).isEqualTo(401);
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

    private int likeCount(long postId) {
        Integer c = jdbc.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Integer.class, postId);
        return c == null ? 0 : c;
    }

    private int commentCount(long postId) {
        Integer c = jdbc.queryForObject(
                "SELECT comment_count FROM posts WHERE id = ?", Integer.class, postId);
        return c == null ? 0 : c;
    }

    private ResponseEntity<String> like(String token, long postId) {
        return rest.exchange(
                "/api/posts/" + postId + "/likes", HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)), String.class);
    }

    private ResponseEntity<String> unlike(String token, long postId) {
        return rest.exchange(
                "/api/posts/" + postId + "/likes", HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(token)), String.class);
    }

    private ResponseEntity<String> addComment(String token, long postId, Map<String, Object> body) {
        return rest.exchange(
                "/api/posts/" + postId + "/comments", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);
    }

    private void deleteComment(String token, long commentId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/comments/" + commentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private ResponseEntity<String> listComments(String token, long postId, Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/posts/" + postId + "/comments?limit={limit}");
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
