package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-080. Asserts the three messaging tables exist
 * with the documented indexes, the partial-unique on
 * {@code conversations(coach_athlete_id)} prevents double-tagging a
 * relationship but lets multiple non-tagged threads coexist, the
 * messages CHECK constraints fire (empty body, body too long, non-
 * positive attachment id), the composite PK on
 * {@code conversation_participants} blocks duplicate joins, and the FK
 * cascades follow the data-model spec:
 *
 * <ul>
 *   <li>delete coach_athlete → conversation.coach_athlete_id becomes
 *       NULL (SET NULL, not CASCADE — the thread history outlives the
 *       relationship).</li>
 *   <li>delete conversation → participants + messages gone.</li>
 *   <li>delete user → their participation rows + their authored messages
 *       gone (CASCADE on both FKs).</li>
 * </ul>
 *
 * <p>No service / controller involved — the schema is the contract;
 * this test guards it so a future migration that drops an index or
 * relaxes a CHECK turns red in CI before the endpoint code that relies
 * on it breaks.
 */
class MessagingSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void all_tables_and_indexes_exist() {
        List<String> tables = List.of(
                "conversations", "conversation_participants", "messages");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        List<String> indexes = List.of(
                "uq_conversations_coach_athlete",
                "idx_conversations_last_message_at",
                "idx_conversation_participants_user",
                "idx_messages_conversation_created");
        for (String index : indexes) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes " +
                            "WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(count).as("index %s exists", index).isEqualTo(1);
        }
    }

    // ── conversations ─────────────────────────────────────────────────────

    @Test
    void conversations_last_message_preview_capped_at_280_chars() {
        String overflow = "x".repeat(281);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversations (last_message_preview) VALUES (?)",
                overflow))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 280 is the documented cap and works.
        jdbc.update(
                "INSERT INTO conversations (last_message_preview) VALUES (?)",
                "x".repeat(280));
    }

    @Test
    void conversations_partial_unique_blocks_double_tagging_one_relationship() {
        long coach = insertThrowawayUser("uq.coach@example.com", "uq.coach");
        long athlete = insertThrowawayUser("uq.athlete@example.com", "uq.athlete");
        long rel = insertRelationship(coach, athlete);

        jdbc.update("INSERT INTO conversations (coach_athlete_id) VALUES (?)", rel);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversations (coach_athlete_id) VALUES (?)", rel))
                .isInstanceOfAny(DuplicateKeyException.class,
                                 DataIntegrityViolationException.class);
    }

    @Test
    void conversations_partial_unique_allows_many_untagged_threads() {
        jdbc.update("INSERT INTO conversations DEFAULT VALUES");
        jdbc.update("INSERT INTO conversations DEFAULT VALUES");
        jdbc.update("INSERT INTO conversations DEFAULT VALUES");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE coach_athlete_id IS NULL",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    // ── messages CHECKs ───────────────────────────────────────────────────

    @Test
    void messages_body_must_be_non_empty() {
        long sender = insertThrowawayUser("body@example.com", "body.sender");
        long convoId = insertEmptyConversation();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body) " +
                        "VALUES (?, ?, '')",
                convoId, sender))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body) " +
                        "VALUES (?, ?, 'hi')",
                convoId, sender);
    }

    @Test
    void messages_body_capped_at_4000_chars() {
        long sender = insertThrowawayUser("cap@example.com", "cap.sender");
        long convoId = insertEmptyConversation();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body) " +
                        "VALUES (?, ?, ?)",
                convoId, sender, "x".repeat(4001)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body) " +
                        "VALUES (?, ?, ?)",
                convoId, sender, "x".repeat(4000));
    }

    @Test
    void messages_attachment_id_must_be_positive_when_set() {
        long sender = insertThrowawayUser("att@example.com", "att.sender");
        long convoId = insertEmptyConversation();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body, attachment_media_id) " +
                        "VALUES (?, ?, 'hi', 0)",
                convoId, sender))
                .isInstanceOf(DataIntegrityViolationException.class);

        // null is fine; positive is fine.
        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, 'no att')",
                convoId, sender);
        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_id, body, attachment_media_id) " +
                        "VALUES (?, ?, 'with att', 7)",
                convoId, sender);
    }

    // ── conversation_participants PK ──────────────────────────────────────

    @Test
    void participants_composite_pk_blocks_duplicate_joins() {
        long user = insertThrowawayUser("pk@example.com", "pk.user");
        long convoId = insertEmptyConversation();

        jdbc.update(
                "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, user);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, user))
                .isInstanceOfAny(DuplicateKeyException.class,
                                 DataIntegrityViolationException.class);
    }

    // ── cascades ──────────────────────────────────────────────────────────

    @Test
    void deleting_coach_athlete_sets_conversation_tag_null_but_keeps_thread() {
        long coach = insertThrowawayUser("set.coach@example.com", "set.coach");
        long athlete = insertThrowawayUser("set.athlete@example.com", "set.athlete");
        long rel = insertRelationship(coach, athlete);

        Long convoId = jdbc.queryForObject(
                "INSERT INTO conversations (coach_athlete_id) VALUES (?) RETURNING id",
                Long.class, rel);

        jdbc.update("DELETE FROM coach_athlete WHERE id = ?", rel);

        // Conversation still there; coach_athlete_id is null now.
        Long tag = jdbc.queryForObject(
                "SELECT coach_athlete_id FROM conversations WHERE id = ?",
                Long.class, convoId);
        assertThat(tag).isNull();
    }

    @Test
    void deleting_conversation_cascades_to_participants_and_messages() {
        long u1 = insertThrowawayUser("c.u1@example.com", "c.u1");
        long u2 = insertThrowawayUser("c.u2@example.com", "c.u2");
        long convoId = insertEmptyConversation();
        jdbc.update("INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, u1);
        jdbc.update("INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, u2);
        jdbc.update("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, 'hi')",
                convoId, u1);

        jdbc.update("DELETE FROM conversations WHERE id = ?", convoId);

        assertThat(countWhere("conversation_participants", "conversation_id", convoId)).isZero();
        assertThat(countWhere("messages", "conversation_id", convoId)).isZero();
    }

    @Test
    void deleting_user_cascades_to_their_participation_and_messages() {
        long sender = insertThrowawayUser("u.sender@example.com", "u.sender");
        long other = insertThrowawayUser("u.other@example.com", "u.other");
        long convoId = insertEmptyConversation();
        jdbc.update("INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, sender);
        jdbc.update("INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                convoId, other);
        jdbc.update("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, 'hi')",
                convoId, sender);

        jdbc.update("DELETE FROM users WHERE id = ?", sender);

        assertThat(countWhere("conversation_participants", "user_id", sender)).isZero();
        assertThat(countWhere("messages", "sender_id", sender)).isZero();
        // Other participant + conversation still there.
        assertThat(countWhere("conversation_participants", "user_id", other)).isEqualTo(1);
        assertThat(countWhere("conversations", "id", convoId)).isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    private long insertRelationship(long coachId, long athleteId) {
        return jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) " +
                        "VALUES (?, ?, 'active') RETURNING id",
                Long.class, coachId, athleteId);
    }

    private long insertEmptyConversation() {
        return jdbc.queryForObject(
                "INSERT INTO conversations DEFAULT VALUES RETURNING id",
                Long.class);
    }

    private int countWhere(String table, String column, long value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
