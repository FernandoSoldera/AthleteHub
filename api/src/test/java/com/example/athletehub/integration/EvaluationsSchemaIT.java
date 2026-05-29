package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-040. Asserts the evaluations + evaluation_measurements
 * tables exist with the expected indexes, the documented CHECK constraints
 * are enforced ({@code bf_method}, {@code source}, {@code kind},
 * {@code unit}, body-fat XOR pairing), the UNIQUE(evaluation_id, point_id)
 * rule actually blocks dupes, and the FK cascades fire the way the
 * data-model spec describes.
 *
 * <p>No service / controller involved — this test guards the schema itself
 * so a future migration that relaxes a CHECK or drops an index surfaces in
 * CI before the runtime code that relies on it breaks.
 */
class EvaluationsSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void tables_and_indexes_exist() {
        List<String> tables = List.of("evaluations", "evaluation_measurements");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        List<String> indexes = List.of(
                "idx_evaluations_user_evaluated",
                "idx_evaluation_measurements_eval");
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
    void bf_method_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("bf@example.com", "bf.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, ?)",
                userId, 80.0, 18.0, "skinfold_3"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Legal method works.
        jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, ?)",
                userId, 80.0, 18.0, "jackson_pollock_7");
    }

    @Test
    void body_fat_and_method_must_agree_on_presence() {
        long userId = insertThrowawayUser("xor@example.com", "xor.user");

        // body_fat_pct without bf_method → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, NULL)",
                userId, 80.0, 18.0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // bf_method without body_fat_pct → rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, NULL, ?)",
                userId, 80.0, "manual"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Both null → ok (weight-only check-in).
        jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg) VALUES (?, ?)",
                userId, 80.0);
        // Both present → ok.
        jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, ?)",
                userId, 80.0, 18.0, "navy");
    }

    @Test
    void source_check_rejects_unknown_values() {
        long userId = insertThrowawayUser("src@example.com", "src.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, source) " +
                        "VALUES (?, ?, ?)",
                userId, 80.0, "import"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, source) " +
                        "VALUES (?, ?, ?)",
                userId, 80.0, "coach");
    }

    @Test
    void weight_and_body_fat_range_checks_fire() {
        long userId = insertThrowawayUser("range@example.com", "range.user");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg) VALUES (?, ?)",
                userId, -1.0))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluations (user_id, weight_kg, body_fat_pct, bf_method) " +
                        "VALUES (?, ?, ?, ?)",
                userId, 80.0, 150.0, "manual"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void measurement_kind_and_unit_checks_fire() {
        long userId = insertThrowawayUser("ku@example.com", "ku.user");
        long evalId = insertEvaluation(userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'neck', 'girth', 'cm', 36.0)",
                evalId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'tricep', 'skinfold', 'inch', 12.0)",
                evalId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'neck', 'circumference', 'cm', 36.0)",
                evalId);
    }

    @Test
    void measurement_value_must_be_non_negative() {
        long userId = insertThrowawayUser("val@example.com", "val.user");
        long evalId = insertEvaluation(userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'arm_r', 'circumference', 'cm', -1.0)",
                evalId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unique_evaluation_point_blocks_dupes() {
        long userId = insertThrowawayUser("uniq@example.com", "uniq.user");
        long evalId = insertEvaluation(userId);

        jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'arm_r', 'circumference', 'cm', 36.0)",
                evalId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'arm_r', 'circumference', 'cm', 36.5)",
                evalId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Different point_id on the same evaluation is fine.
        jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'arm_l', 'circumference', 'cm', 36.5)",
                evalId);
    }

    // ── cascades ──────────────────────────────────────────────────────────

    @Test
    void deleting_an_evaluation_cascades_to_measurements() {
        long userId = insertThrowawayUser("casc1@example.com", "casc1.user");
        long evalId = insertEvaluation(userId);
        jdbc.update(
                "INSERT INTO evaluation_measurements (evaluation_id, point_id, kind, unit, value) " +
                        "VALUES (?, 'neck', 'circumference', 'cm', 36.0)",
                evalId);

        jdbc.update("DELETE FROM evaluations WHERE id = ?", evalId);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evaluation_measurements WHERE evaluation_id = ?",
                Integer.class, evalId);
        assertThat(remaining).isZero();
    }

    @Test
    void deleting_a_user_cascades_to_their_evaluations() {
        long userId = insertThrowawayUser("casc2@example.com", "casc2.user");
        long evalId = insertEvaluation(userId);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evaluations WHERE id = ?", Integer.class, evalId);
        assertThat(remaining).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    private long insertEvaluation(long userId) {
        return jdbc.queryForObject(
                "INSERT INTO evaluations (user_id, weight_kg) VALUES (?, ?) RETURNING id",
                Long.class, userId, 80.0);
    }
}
