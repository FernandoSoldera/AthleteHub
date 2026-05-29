package com.example.athletehub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only test for AH-050. Asserts the 6 nutrition tables exist with
 * the expected indexes, the documented CHECK constraints fire
 * (foods XOR, unit / source enums, non-negative macros), uniqueness rules
 * actually block dupes, and the FK cascades follow the data-model spec
 * (delete plan → meals → items; delete user → diary + favorites +
 * plans; food deletion RESTRICTed when referenced by a plan / diary;
 * favorites CASCADE when the food disappears).
 *
 * <p>No service / controller involved — the schema is the contract; this
 * test guards it so a future migration that drops an index or relaxes a
 * CHECK turns red in CI before any runtime code that relies on it breaks.
 */
class NutritionSchemaIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    // ── presence ──────────────────────────────────────────────────────────

    @Test
    void all_tables_and_indexes_exist() {
        List<String> tables = List.of(
                "foods",
                "diet_plans",
                "diet_meals",
                "meal_items",
                "diary_entries",
                "favorites");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        List<String> indexes = List.of(
                "idx_foods_name_lower",
                "idx_foods_created_by",
                "idx_diet_plans_owner",
                "idx_diet_meals_plan",
                "idx_meal_items_meal",
                "idx_diary_entries_user_eaten",
                "idx_favorites_user");
        for (String index : indexes) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes " +
                            "WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(count).as("index %s exists", index).isEqualTo(1);
        }
    }

    // ── foods constraints ────────────────────────────────────────────────

    @Test
    void foods_xor_constraint_blocks_global_with_owner() {
        long userId = insertThrowawayUser("xor@example.com", "xor.user");

        // is_global = true AND created_by != null → rejected.
        assertThatThrownBy(() -> insertFood("Bench", true, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // is_global = false AND created_by = null → rejected.
        assertThatThrownBy(() -> insertFood("Squat", false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Both legal shapes succeed.
        insertFood("Chicken Breast", true, null);
        insertFood("Custom Shake", false, userId);
    }

    @Test
    void foods_macros_must_be_non_negative() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO foods (name, is_global, serving_size_g, kcal, protein_g, carb_g, fat_g) " +
                        "VALUES (?, true, 100, -10, 20, 0, 1)",
                "BadFood"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foods_serving_size_must_be_positive() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO foods (name, is_global, serving_size_g, kcal, protein_g, carb_g, fat_g) " +
                        "VALUES (?, true, 0, 100, 20, 0, 1)",
                "ZeroServing"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── meal_items / diary_entries enums ─────────────────────────────────

    @Test
    void meal_items_unit_check_rejects_unknown_values() {
        long ownerId = insertThrowawayUser("mi@example.com", "mi.user");
        long planId = insertPlan(ownerId, "Cut");
        long mealId = insertMeal(planId, 0, "Breakfast");
        long foodId = insertFood("Oats", true, null);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 50, 'oz', 0)",
                mealId, foodId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 50, 'g', 0)",
                mealId, foodId);
    }

    @Test
    void diary_entries_source_check_accepts_coach_and_rejects_unknown() {
        long userId = insertThrowawayUser("ds@example.com", "ds.user");
        long foodId = insertFood("Banana", true, null);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO diary_entries (user_id, food_id, amount, unit, source) " +
                        "VALUES (?, ?, 120, 'g', 'import')",
                userId, foodId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 'coach' value is reserved for Epic 7 assignments but already accepted.
        jdbc.update(
                "INSERT INTO diary_entries (user_id, food_id, amount, unit, source) " +
                        "VALUES (?, ?, 120, 'g', 'coach')",
                userId, foodId);
    }

    // ── uniqueness ───────────────────────────────────────────────────────

    @Test
    void diet_meals_position_unique_per_plan() {
        long ownerId = insertThrowawayUser("dm@example.com", "dm.user");
        long planId = insertPlan(ownerId, "Maintenance");
        insertMeal(planId, 0, "Breakfast");

        assertThatThrownBy(() -> insertMeal(planId, 0, "Second breakfast"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Different position is fine.
        insertMeal(planId, 1, "Lunch");
    }

    @Test
    void meal_items_position_unique_per_meal() {
        long ownerId = insertThrowawayUser("mip@example.com", "mip.user");
        long planId = insertPlan(ownerId, "Plan");
        long mealId = insertMeal(planId, 0, "Breakfast");
        long foodId = insertFood("Eggs", true, null);

        jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 100, 'g', 0)",
                mealId, foodId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 50, 'g', 0)",
                mealId, foodId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void favorites_unique_per_user_food() {
        long userId = insertThrowawayUser("fav@example.com", "fav.user");
        long foodId = insertFood("Tuna", true, null);

        jdbc.update("INSERT INTO favorites (user_id, food_id) VALUES (?, ?)", userId, foodId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO favorites (user_id, food_id) VALUES (?, ?)", userId, foodId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── cascades ─────────────────────────────────────────────────────────

    @Test
    void deleting_a_plan_cascades_to_meals_and_items() {
        long ownerId = insertThrowawayUser("c1@example.com", "c1.user");
        long planId = insertPlan(ownerId, "Cut");
        long mealId = insertMeal(planId, 0, "Breakfast");
        long foodId = insertFood("Oats", true, null);
        jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 50, 'g', 0)",
                mealId, foodId);

        jdbc.update("DELETE FROM diet_plans WHERE id = ?", planId);

        assertThat(countWhere("diet_meals", "plan_id", planId)).isZero();
        assertThat(countWhere("meal_items", "meal_id", mealId)).isZero();
    }

    @Test
    void deleting_a_food_is_blocked_when_referenced_by_a_meal_or_diary() {
        long ownerId = insertThrowawayUser("c2@example.com", "c2.user");
        long planId = insertPlan(ownerId, "Plan");
        long mealId = insertMeal(planId, 0, "Breakfast");
        long foodId = insertFood("Chicken", true, null);
        jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, 200, 'g', 0)",
                mealId, foodId);

        // Meal references it → DELETE blocked.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM foods WHERE id = ?", foodId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Same with diary entry.
        jdbc.update(
                "INSERT INTO diary_entries (user_id, food_id, amount, unit) " +
                        "VALUES (?, ?, 150, 'g')",
                ownerId, foodId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM foods WHERE id = ?", foodId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleting_a_food_cascades_to_favorites() {
        long userId = insertThrowawayUser("c3@example.com", "c3.user");
        long foodId = insertFood("Apple", true, null);
        jdbc.update("INSERT INTO favorites (user_id, food_id) VALUES (?, ?)", userId, foodId);

        jdbc.update("DELETE FROM foods WHERE id = ?", foodId);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE food_id = ?", Integer.class, foodId);
        assertThat(remaining).isZero();
    }

    @Test
    void deleting_a_user_cascades_to_plans_diary_and_favorites() {
        long userId = insertThrowawayUser("c4@example.com", "c4.user");
        long foodId = insertFood("Rice", true, null);
        long planId = insertPlan(userId, "Plan");
        jdbc.update(
                "INSERT INTO diary_entries (user_id, food_id, amount, unit) " +
                        "VALUES (?, ?, 200, 'g')",
                userId, foodId);
        jdbc.update("INSERT INTO favorites (user_id, food_id) VALUES (?, ?)", userId, foodId);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(countWhere("diet_plans", "owner_id", userId)).isZero();
        assertThat(countWhere("diary_entries", "user_id", userId)).isZero();
        assertThat(countWhere("favorites", "user_id", userId)).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private long insertThrowawayUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, full_name, handle) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, email, "Test", handle);
    }

    private long insertFood(String name, boolean isGlobal, Long createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO foods (name, is_global, created_by, serving_size_g, " +
                        "kcal, protein_g, carb_g, fat_g) " +
                        "VALUES (?, ?, ?, 100, 165, 31, 0, 3.6) RETURNING id",
                Long.class, name, isGlobal, createdBy);
    }

    private long insertPlan(long ownerId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO diet_plans (owner_id, name) VALUES (?, ?) RETURNING id",
                Long.class, ownerId, name);
    }

    private long insertMeal(long planId, int position, String name) {
        return jdbc.queryForObject(
                "INSERT INTO diet_meals (plan_id, position, name) VALUES (?, ?, ?) RETURNING id",
                Long.class, planId, position, name);
    }

    private int countWhere(String table, String column, long value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
