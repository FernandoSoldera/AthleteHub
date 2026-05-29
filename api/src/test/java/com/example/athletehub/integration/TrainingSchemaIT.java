package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-030. Asserts each training table exists with the
 * expected columns, the documented indexes were created, the
 * "exactly one of is_global / created_by" rule on exercises is enforced,
 * status / metric / type / source enums reject bad values, and the FK
 * cascades fire the way the data-model spec describes.
 *
 * <p>No service / controller layer involved — this test guards the schema
 * itself so a future migration that drops an index or relaxes a CHECK
 * surfaces in CI before the runtime code that relies on it breaks.
 */
class TrainingSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void all_training_tables_exist() {
        List<String> tables = List.of(
                "exercises",
                "workout_templates",
                "workout_template_exercises",
                "workout_sessions",
                "session_exercises",
                "exercise_sets",
                "personal_records",
                "cardio_activities");

        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }
    }

    @Test
    void documented_indexes_were_created() {
        List<String> indexes = List.of(
                "idx_exercises_name_lower",
                "idx_exercises_created_by",
                "idx_workout_templates_owner",
                "idx_wte_template",
                "idx_workout_sessions_user_started",
                "idx_workout_sessions_user_active",
                "idx_session_exercises_session",
                "idx_personal_records_user",
                "idx_cardio_activities_user_started");

        for (String index : indexes) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes " +
                            "WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(count).as("index %s exists", index).isEqualTo(1);
        }
    }

    // ── constraints ───────────────────────────────────────────────────────

    @Test
    void exercises_xor_constraint_blocks_global_with_owner() {
        long userId = insertThrowawayUser("xor1@example.com", "xor.one");

        // is_global = true AND created_by != null → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO exercises (name, is_global, created_by) VALUES (?, true, ?)",
                "Bench", userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // is_global = false AND created_by = null → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO exercises (name, is_global, created_by) VALUES (?, false, NULL)",
                "Squat"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Both legal shapes succeed.
        jdbc.update("INSERT INTO exercises (name, is_global, created_by) VALUES (?, true, NULL)", "Bench");
        jdbc.update("INSERT INTO exercises (name, is_global, created_by) VALUES (?, false, ?)", "Custom curl", userId);
    }

    @Test
    void workout_sessions_status_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("status@example.com", "status.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workout_sessions (user_id, title, status) VALUES (?, ?, ?)",
                userId, "Bad", "PAUSED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO workout_sessions (user_id, title, status) VALUES (?, ?, ?)",
                userId, "Good", "in_progress");
    }

    @Test
    void personal_records_unique_per_user_exercise_metric() {
        long userId = insertThrowawayUser("pr@example.com", "pr.user");
        Long exerciseId = jdbc.queryForObject(
                "INSERT INTO exercises (name, is_global) VALUES ('Bench', true) RETURNING id",
                Long.class);

        jdbc.update(
                "INSERT INTO personal_records (user_id, exercise_id, metric, value, achieved_at) " +
                        "VALUES (?, ?, 'e1rm', 100, NOW())",
                userId, exerciseId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO personal_records (user_id, exercise_id, metric, value, achieved_at) " +
                        "VALUES (?, ?, 'e1rm', 110, NOW())",
                userId, exerciseId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Different metric on the same (user, exercise) is allowed.
        jdbc.update(
                "INSERT INTO personal_records (user_id, exercise_id, metric, value, achieved_at) " +
                        "VALUES (?, ?, 'max_weight', 105, NOW())",
                userId, exerciseId);
    }

    @Test
    void cardio_activities_type_and_source_checks_are_enforced() {
        long userId = insertThrowawayUser("cardio@example.com", "cardio.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cardio_activities (user_id, type, distance_m, duration_seconds) " +
                        "VALUES (?, 'swim', 1000, 600)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cardio_activities (user_id, type, distance_m, duration_seconds, source) " +
                        "VALUES (?, 'run', 1000, 600, 'manual')",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO cardio_activities (user_id, type, distance_m, duration_seconds) " +
                        "VALUES (?, 'run', 5000, 1800)",
                userId);
    }

    // ── cascades ──────────────────────────────────────────────────────────

    @Test
    void deleting_a_session_cascades_to_session_exercises_and_sets() {
        long userId = insertThrowawayUser("cascade@example.com", "cascade.user");
        Long exerciseId = jdbc.queryForObject(
                "INSERT INTO exercises (name, is_global) VALUES ('Bench', true) RETURNING id",
                Long.class);
        Long sessionId = jdbc.queryForObject(
                "INSERT INTO workout_sessions (user_id, title) VALUES (?, 'Push') RETURNING id",
                Long.class, userId);
        Long seId = jdbc.queryForObject(
                "INSERT INTO session_exercises (session_id, exercise_id, position) " +
                        "VALUES (?, ?, 0) RETURNING id",
                Long.class, sessionId, exerciseId);
        jdbc.update(
                "INSERT INTO exercise_sets (session_exercise_id, set_number, weight_kg, reps, is_done) " +
                        "VALUES (?, 1, 80, 5, true)",
                seId);

        jdbc.update("DELETE FROM workout_sessions WHERE id = ?", sessionId);

        assertThat(countRowsWhere("session_exercises", "session_id", sessionId)).isZero();
        assertThat(countRowsWhere("exercise_sets", "session_exercise_id", seId)).isZero();
    }

    @Test
    void deleting_an_exercise_is_blocked_when_a_session_uses_it() {
        long userId = insertThrowawayUser("restrict@example.com", "restrict.user");
        Long exerciseId = jdbc.queryForObject(
                "INSERT INTO exercises (name, is_global) VALUES ('Bench', true) RETURNING id",
                Long.class);
        Long sessionId = jdbc.queryForObject(
                "INSERT INTO workout_sessions (user_id, title) VALUES (?, 'Push') RETURNING id",
                Long.class, userId);
        jdbc.update(
                "INSERT INTO session_exercises (session_id, exercise_id, position) VALUES (?, ?, 0)",
                sessionId, exerciseId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM exercises WHERE id = ?", exerciseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleting_a_template_nulls_session_template_id() {
        long userId = insertThrowawayUser("setnull@example.com", "setnull.user");
        Long templateId = jdbc.queryForObject(
                "INSERT INTO workout_templates (owner_id, name) VALUES (?, 'PPL') RETURNING id",
                Long.class, userId);
        Long sessionId = jdbc.queryForObject(
                "INSERT INTO workout_sessions (user_id, template_id, title) VALUES (?, ?, 'Push') RETURNING id",
                Long.class, userId, templateId);

        jdbc.update("DELETE FROM workout_templates WHERE id = ?", templateId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT template_id FROM workout_sessions WHERE id = ?", sessionId);
        assertThat(row.get("template_id")).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    private int countRowsWhere(String table, String column, long value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
