package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-060. Asserts the three feed tables exist with
 * the documented indexes, the documented CHECK constraints fire
 * ({@code type}, {@code visibility}, {@code source_ref_*} XOR,
 * non-negative counters, non-empty comment body), the composite primary
 * key on post_likes blocks duplicate likes, and the FK cascades follow
 * the data-model spec (delete post → likes + comments; delete user →
 * their posts → likes + comments via the chain).
 *
 * <p>No service / controller involved — the schema is the contract; this
 * test guards it so a future migration that drops an index or relaxes a
 * CHECK turns red in CI before runtime code that relies on it breaks.
 */
class FeedSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void all_tables_and_indexes_exist() {
        List<String> tables = List.of("posts", "post_likes", "post_comments");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        List<String> indexes = List.of(
                "idx_posts_author_created",
                "idx_posts_feed_created_active",
                "idx_post_likes_user",
                "idx_post_comments_post_created");
        for (String index : indexes) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes " +
                            "WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(count).as("index %s exists", index).isEqualTo(1);
        }
    }

    // ── posts CHECKs ──────────────────────────────────────────────────────

    @Test
    void posts_type_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("type@example.com", "type.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type) VALUES (?, ?)",
                userId, "rant"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Each documented value works.
        for (String type : new String[]{"workout", "run", "cycle", "evolution", "manual"}) {
            jdbc.update("INSERT INTO posts (author_id, type) VALUES (?, ?)", userId, type);
        }
    }

    @Test
    void posts_visibility_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("vis@example.com", "vis.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, visibility) VALUES (?, 'manual', 'secret')",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO posts (author_id, type, visibility) VALUES (?, 'manual', 'public')",
                userId);
    }

    @Test
    void posts_source_ref_xor_enforced() {
        long userId = insertThrowawayUser("xor@example.com", "xor.user");

        // type set, id null → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, source_ref_type, source_ref_id) " +
                        "VALUES (?, 'workout', 'workout_session', NULL)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // type null, id set → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, source_ref_type, source_ref_id) " +
                        "VALUES (?, 'workout', NULL, 42)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Both null → ok (manual post).
        jdbc.update(
                "INSERT INTO posts (author_id, type) VALUES (?, 'manual')",
                userId);

        // Both set → ok (auto-post from a workout).
        jdbc.update(
                "INSERT INTO posts (author_id, type, source_ref_type, source_ref_id) " +
                        "VALUES (?, 'workout', 'workout_session', 42)",
                userId);
    }

    @Test
    void posts_source_ref_type_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("srt@example.com", "srt.user");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, source_ref_type, source_ref_id) " +
                        "VALUES (?, 'workout', 'pr_record', 7)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void posts_counters_must_be_non_negative() {
        long userId = insertThrowawayUser("cnt@example.com", "cnt.user");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, like_count) VALUES (?, 'manual', -1)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (author_id, type, comment_count) VALUES (?, 'manual', -3)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void posts_counters_default_to_zero() {
        long userId = insertThrowawayUser("def@example.com", "def.user");
        long postId = insertPost(userId, "manual");

        Integer like = jdbc.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Integer.class, postId);
        Integer comment = jdbc.queryForObject(
                "SELECT comment_count FROM posts WHERE id = ?", Integer.class, postId);
        assertThat(like).isZero();
        assertThat(comment).isZero();
    }

    // ── post_likes PK ─────────────────────────────────────────────────────

    @Test
    void post_likes_pk_blocks_duplicate_likes() {
        long userId = insertThrowawayUser("dup@example.com", "dup.user");
        long postId = insertPost(userId, "manual");

        jdbc.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                postId, userId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                postId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── post_comments CHECK ───────────────────────────────────────────────

    @Test
    void post_comments_body_must_be_non_empty() {
        long userId = insertThrowawayUser("body@example.com", "body.user");
        long postId = insertPost(userId, "manual");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO post_comments (post_id, author_id, body) VALUES (?, ?, '')",
                postId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO post_comments (post_id, author_id, body) VALUES (?, ?, 'Nice lift!')",
                postId, userId);
    }

    // ── cascades ──────────────────────────────────────────────────────────

    @Test
    void deleting_a_post_cascades_to_likes_and_comments() {
        long author = insertThrowawayUser("c1@example.com", "c1.user");
        long liker = insertThrowawayUser("c1b@example.com", "c1b.user");
        long postId = insertPost(author, "manual");
        jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)", postId, liker);
        jdbc.update(
                "INSERT INTO post_comments (post_id, author_id, body) VALUES (?, ?, 'hi')",
                postId, liker);

        jdbc.update("DELETE FROM posts WHERE id = ?", postId);

        assertThat(countWhere("post_likes", "post_id", postId)).isZero();
        assertThat(countWhere("post_comments", "post_id", postId)).isZero();
    }

    @Test
    void deleting_a_user_cascades_to_their_posts_likes_and_comments() {
        long author = insertThrowawayUser("c2@example.com", "c2.user");
        long other = insertThrowawayUser("c2b@example.com", "c2b.user");
        long otherPostId = insertPost(other, "manual");
        long authorPostId = insertPost(author, "manual");

        // author likes + comments on other's post.
        jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                otherPostId, author);
        jdbc.update(
                "INSERT INTO post_comments (post_id, author_id, body) VALUES (?, ?, 'hi')",
                otherPostId, author);

        jdbc.update("DELETE FROM users WHERE id = ?", author);

        // author's own post is gone.
        assertThat(countWhere("posts", "id", authorPostId)).isZero();
        // author's like / comment on other's post are gone too.
        assertThat(countWhere("post_likes", "user_id", author)).isZero();
        assertThat(countWhere("post_comments", "author_id", author)).isZero();
        // other's post survives.
        assertThat(countWhere("posts", "id", otherPostId)).isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    private long insertPost(long authorId, String type) {
        return jdbc.queryForObject(
                "INSERT INTO posts (author_id, type) VALUES (?, ?) RETURNING id",
                Long.class, authorId, type);
    }

    private int countWhere(String table, String column, long value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
