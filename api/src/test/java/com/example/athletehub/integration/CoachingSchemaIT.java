package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-070. Asserts the 4 coaching tables exist with
 * the documented indexes, the CHECK constraints fire (status enums,
 * range checks, can't-coach-yourself, assignment ref XOR), the
 * UNIQUE(coach_id, athlete_id) on coach_athlete blocks duplicate
 * relationships, and the cascade matrix follows the spec — including
 * the 3 ALTERed FK columns on workout_sessions / cardio_activities /
 * evaluations that this migration wires up.
 */
class CoachingSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void all_tables_and_indexes_exist() {
        List<String> tables = List.of(
                "coach_athlete", "assignments", "eval_requests", "coach_profiles");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        List<String> indexes = List.of(
                "idx_coach_athlete_coach_flag",
                "idx_coach_athlete_athlete",
                "idx_assignments_relationship_date",
                "idx_eval_requests_relationship_scheduled",
                "idx_workout_sessions_assignment",
                "idx_cardio_activities_assignment",
                "idx_evaluations_eval_request");
        for (String index : indexes) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes " +
                            "WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(count).as("index %s exists", index).isEqualTo(1);
        }
    }

    // ── ALTER columns landed ──────────────────────────────────────────────

    @Test
    void earlier_tables_got_their_assignment_columns() {
        for (String t : List.of("workout_sessions", "cardio_activities")) {
            Integer present = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_name = ? AND column_name = 'assignment_id'",
                    Integer.class, t);
            assertThat(present).as("%s.assignment_id exists", t).isEqualTo(1);
        }
        Integer evalCol = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'evaluations' AND column_name = 'eval_request_id'",
                Integer.class);
        assertThat(evalCol).isEqualTo(1);
    }

    // ── coach_athlete constraints ────────────────────────────────────────

    @Test
    void coach_athlete_status_check_rejects_unknown_values() {
        long coach = insertThrowawayUser("coach@example.com", "the.coach");
        long athlete = insertThrowawayUser("athlete@example.com", "the.athlete");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) " +
                        "VALUES (?, ?, 'maybe')",
                coach, athlete))
                .isInstanceOf(DataIntegrityViolationException.class);

        // All three legal values land.
        for (String s : new String[]{"pending", "active", "ended"}) {
            jdbc.update("DELETE FROM coach_athlete WHERE coach_id = ? AND athlete_id = ?",
                    coach, athlete);
            jdbc.update(
                    "INSERT INTO coach_athlete (coach_id, athlete_id, status) " +
                            "VALUES (?, ?, ?)",
                    coach, athlete, s);
        }
    }

    @Test
    void coach_athlete_cannot_coach_yourself() {
        long userId = insertThrowawayUser("self@example.com", "self.coach");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id) VALUES (?, ?)",
                userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void coach_athlete_unique_per_pair() {
        long coach = insertThrowawayUser("uniq.coach@example.com", "uniq.coach");
        long athlete = insertThrowawayUser("uniq.athlete@example.com", "uniq.athlete");

        jdbc.update("INSERT INTO coach_athlete (coach_id, athlete_id) VALUES (?, ?)",
                coach, athlete);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id) VALUES (?, ?)",
                coach, athlete))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void coach_athlete_flag_and_adherence_range_checks() {
        long coach = insertThrowawayUser("flag@example.com", "flag.coach");
        long athlete = insertThrowawayUser("flagAth@example.com", "flag.athlete");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id, flag) VALUES (?, ?, 'great')",
                coach, athlete))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id, adherence_pct) VALUES (?, ?, 150)",
                coach, athlete))
                .isInstanceOf(DataIntegrityViolationException.class);

        // All three flag values + boundary adherence land.
        for (String f : new String[]{"on_track", "attention", "risk"}) {
            jdbc.update("DELETE FROM coach_athlete WHERE coach_id = ? AND athlete_id = ?",
                    coach, athlete);
            jdbc.update(
                    "INSERT INTO coach_athlete (coach_id, athlete_id, flag, adherence_pct) " +
                            "VALUES (?, ?, ?, 100)",
                    coach, athlete, f);
        }
    }

    // ── assignments constraints ──────────────────────────────────────────

    @Test
    void assignments_type_check_rejects_unknown_values() {
        long relId = insertRelationship();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'morale_boost')",
                relId))
                .isInstanceOf(DataIntegrityViolationException.class);

        for (String t : new String[]{"workout", "diet", "eval"}) {
            jdbc.update("INSERT INTO assignments (coach_athlete_id, type) VALUES (?, ?)",
                    relId, t);
        }
    }

    @Test
    void assignments_status_check_rejects_unknown_values() {
        long relId = insertRelationship();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type, status) VALUES (?, 'workout', 'forgotten')",
                relId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assignments_ref_pair_xor_enforced() {
        long relId = insertRelationship();

        // ref_type set, ref_id null → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type, ref_type, ref_id) " +
                        "VALUES (?, 'workout', 'workout_template', NULL)",
                relId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // ref_type null, ref_id set → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type, ref_type, ref_id) " +
                        "VALUES (?, 'workout', NULL, 42)",
                relId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Both null → ok ("free" assignment with just notes).
        jdbc.update("INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout')",
                relId);
        // Both set → ok.
        jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type, ref_type, ref_id) " +
                        "VALUES (?, 'workout', 'workout_template', 42)",
                relId);
    }

    // ── eval_requests CHECK ──────────────────────────────────────────────

    @Test
    void eval_requests_status_check_rejects_unknown_values() {
        long relId = insertRelationship();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO eval_requests (coach_athlete_id, scheduled_for, status) " +
                        "VALUES (?, NOW(), 'snoozed')",
                relId))
                .isInstanceOf(DataIntegrityViolationException.class);

        for (String s : new String[]{"scheduled", "completed", "missed"}) {
            jdbc.update(
                    "INSERT INTO eval_requests (coach_athlete_id, scheduled_for, status) " +
                            "VALUES (?, NOW(), ?)",
                    relId, s);
        }
    }

    // ── coach_profiles range checks ──────────────────────────────────────

    @Test
    void coach_profiles_rating_average_in_zero_to_five() {
        long coach = insertThrowawayUser("cp@example.com", "cp.coach");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coach_profiles (user_id, rating_avg) VALUES (?, 6.0)",
                coach))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Boundary values land.
        jdbc.update("INSERT INTO coach_profiles (user_id, rating_avg) VALUES (?, 0.0)", coach);
        jdbc.update("DELETE FROM coach_profiles WHERE user_id = ?", coach);
        jdbc.update("INSERT INTO coach_profiles (user_id, rating_avg) VALUES (?, 5.0)", coach);
    }

    // ── cascades ─────────────────────────────────────────────────────────

    @Test
    void deleting_coach_athlete_cascades_to_assignments_and_eval_requests() {
        long relId = insertRelationship();
        long assignmentId = jdbc.queryForObject(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout') RETURNING id",
                Long.class, relId);
        long evalRequestId = jdbc.queryForObject(
                "INSERT INTO eval_requests (coach_athlete_id, scheduled_for) VALUES (?, NOW()) RETURNING id",
                Long.class, relId);

        jdbc.update("DELETE FROM coach_athlete WHERE id = ?", relId);

        assertThat(countWhere("assignments", "id", assignmentId)).isZero();
        assertThat(countWhere("eval_requests", "id", evalRequestId)).isZero();
    }

    @Test
    void deleting_coach_user_cascades_to_relationship_and_profile() {
        long coach = insertThrowawayUser("u1@example.com", "u1.coach");
        long athlete = insertThrowawayUser("u1Ath@example.com", "u1.athlete");
        long relId = jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id) VALUES (?, ?) RETURNING id",
                Long.class, coach, athlete);
        jdbc.update("INSERT INTO coach_profiles (user_id, headline) VALUES (?, ?)",
                coach, "I lift things up");

        jdbc.update("DELETE FROM users WHERE id = ?", coach);

        assertThat(countWhere("coach_athlete", "id", relId)).isZero();
        assertThat(countWhere("coach_profiles", "user_id", coach)).isZero();
    }

    @Test
    void deleting_assignment_nulls_workout_session_assignment_id() {
        long relId = insertRelationship();
        long assignmentId = jdbc.queryForObject(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout') RETURNING id",
                Long.class, relId);
        // Use the same athlete the relationship points at.
        Long athleteId = jdbc.queryForObject(
                "SELECT athlete_id FROM coach_athlete WHERE id = ?", Long.class, relId);
        long sessionId = jdbc.queryForObject(
                "INSERT INTO workout_sessions (user_id, title, assignment_id) " +
                        "VALUES (?, 'Push A', ?) RETURNING id",
                Long.class, athleteId, assignmentId);

        jdbc.update("DELETE FROM assignments WHERE id = ?", assignmentId);

        Long stillSetTo = jdbc.queryForObject(
                "SELECT assignment_id FROM workout_sessions WHERE id = ?", Long.class, sessionId);
        assertThat(stillSetTo).isNull();
        // Session itself survives.
        assertThat(countWhere("workout_sessions", "id", sessionId)).isEqualTo(1);
    }

    @Test
    void deleting_assignment_nulls_cardio_activity_assignment_id() {
        long relId = insertRelationship();
        long assignmentId = jdbc.queryForObject(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout') RETURNING id",
                Long.class, relId);
        Long athleteId = jdbc.queryForObject(
                "SELECT athlete_id FROM coach_athlete WHERE id = ?", Long.class, relId);
        long cardioId = jdbc.queryForObject(
                "INSERT INTO cardio_activities (user_id, type, distance_m, duration_seconds, assignment_id) " +
                        "VALUES (?, 'run', 5000, 1800, ?) RETURNING id",
                Long.class, athleteId, assignmentId);

        jdbc.update("DELETE FROM assignments WHERE id = ?", assignmentId);

        Long stillSetTo = jdbc.queryForObject(
                "SELECT assignment_id FROM cardio_activities WHERE id = ?", Long.class, cardioId);
        assertThat(stillSetTo).isNull();
    }

    @Test
    void deleting_eval_request_nulls_evaluation_eval_request_id() {
        long relId = insertRelationship();
        long requestId = jdbc.queryForObject(
                "INSERT INTO eval_requests (coach_athlete_id, scheduled_for) " +
                        "VALUES (?, NOW()) RETURNING id",
                Long.class, relId);
        Long athleteId = jdbc.queryForObject(
                "SELECT athlete_id FROM coach_athlete WHERE id = ?", Long.class, relId);
        long evalId = jdbc.queryForObject(
                "INSERT INTO evaluations (user_id, weight_kg, eval_request_id) " +
                        "VALUES (?, 80, ?) RETURNING id",
                Long.class, athleteId, requestId);

        jdbc.update("DELETE FROM eval_requests WHERE id = ?", requestId);

        Long stillSetTo = jdbc.queryForObject(
                "SELECT eval_request_id FROM evaluations WHERE id = ?", Long.class, evalId);
        assertThat(stillSetTo).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    /** Stand up a fresh coach + athlete + relationship, returning the relationship id. */
    private long insertRelationship() {
        long coach = insertThrowawayUser("c" + System.nanoTime() + "@example.com",
                "c.coach." + System.nanoTime());
        long athlete = insertThrowawayUser("a" + System.nanoTime() + "@example.com",
                "a.athlete." + System.nanoTime());
        return jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id) VALUES (?, ?) RETURNING id",
                Long.class, coach, athlete);
    }

    private int countWhere(String table, String column, long value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
