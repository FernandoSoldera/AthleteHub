package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-052 — active diet plan + day endpoint. Fixed-
 * clock pin (Wed 2026-05-27) makes the day window deterministic. Plans
 * and diary rows are seeded via {@link JdbcTemplate} because there's no
 * plan-CRUD endpoint yet (lands later, like template-CRUD in Epic 3).
 *
 * <p>Macro math is verified concretely: 200g chicken (165 kcal / 100 g)
 * should land at 330 kcal; a diary entry of 1 portion of the same food
 * should land at 165 kcal, etc.
 */
@Import(DietActiveAndDayIT.FixedClockConfig.class)
class DietActiveAndDayIT extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String PASSWORD = "supersecret1!";

    /** "Today" — same Wednesday as the other fixed-clock tests. */
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 5, 27);

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private long aliceId;
    private long bobId;
    private long chickenId;
    private long riceId;

    @BeforeEach
    void setUp() {
        // Wipe in dependency order.
        jdbc.update("UPDATE users SET active_diet_plan_id = NULL");
        jdbc.update("DELETE FROM diary_entries");
        jdbc.update("DELETE FROM favorites");
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM diet_meals");
        jdbc.update("DELETE FROM diet_plans");
        jdbc.update("DELETE FROM foods WHERE created_by IS NOT NULL");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(ALICE_EMAIL, "Alice A.", "alice.lifts");
        register(BOB_EMAIL, "Bob B.", "bob.runs");
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ALICE_EMAIL);
        bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, BOB_EMAIL);
        aliceToken = authToken(ALICE_EMAIL);
        bobToken = authToken(BOB_EMAIL);

        chickenId = foodIdByName("Chicken Breast (cooked)");
        riceId = foodIdByName("White Rice (cooked)");
    }

    // ── active plan ──────────────────────────────────────────────────────

    @Test
    void active_plan_is_null_when_no_plan_set() {
        ResponseEntity<String> response = getActive(aliceToken);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // null serializes as empty body.
        assertThat(response.getBody()).isIn(null, "", "null");
    }

    @Test
    void set_active_then_get_returns_hydrated_plan_with_daily_target() {
        long planId = createPlan(aliceId, "Maintenance");
        long breakfastId = createMeal(planId, 0, "Breakfast", "07:30");
        addMealItem(breakfastId, 0, chickenId, 200, "g");   // 330 kcal
        addMealItem(breakfastId, 1, riceId, 150, "g");       // 195 kcal
        // → 525 kcal, 62.0 + 4.05 = 66.05 g protein

        setActive(aliceToken, planId);
        JsonNode body = json(getActive(aliceToken).getBody());
        assertThat(body.get("id").asLong()).isEqualTo(planId);
        assertThat(body.get("name").asText()).isEqualTo("Maintenance");
        assertThat(body.get("meals").size()).isEqualTo(1);
        JsonNode breakfast = body.get("meals").get(0);
        assertThat(breakfast.get("name").asText()).isEqualTo("Breakfast");
        assertThat(breakfast.get("timeHint").asText()).isEqualTo("07:30");
        assertThat(breakfast.get("items").size()).isEqualTo(2);
        // Item-level macros surface.
        assertThat(breakfast.get("items").get(0).get("macros").get("kcal").decimalValue().doubleValue())
                .isEqualTo(330.00);
        // Plan-level daily target.
        assertThat(body.get("dailyTarget").get("kcal").decimalValue().doubleValue())
                .isEqualTo(525.00);
        assertThat(body.get("dailyTarget").get("proteinG").decimalValue().doubleValue())
                .isEqualTo(66.05);
    }

    @Test
    void set_active_clears_active_plan_when_planId_null() {
        long planId = createPlan(aliceId, "Plan");
        setActive(aliceToken, planId);
        JsonNode set1 = json(getActive(aliceToken).getBody());
        assertThat(set1.get("id").asLong()).isEqualTo(planId);

        // Clear with explicit null.
        setActive(aliceToken, null);
        ResponseEntity<String> cleared = getActive(aliceToken);
        assertThat(cleared.getStatusCode().value()).isEqualTo(200);
        assertThat(cleared.getBody()).isIn(null, "", "null");
    }

    @Test
    void set_active_rejects_another_users_plan() {
        long planId = createPlan(bobId, "Bob's plan");
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/active",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("planId", planId), bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.DIET_PLAN_NOT_FOUND.name());
    }

    @Test
    void set_active_rejects_unknown_plan() {
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/active",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("planId", 999_999L), bearer(aliceToken)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleting_active_plan_nulls_users_active_pointer() {
        long planId = createPlan(aliceId, "P");
        setActive(aliceToken, planId);
        jdbc.update("DELETE FROM diet_plans WHERE id = ?", planId);

        Long active = jdbc.queryForObject(
                "SELECT active_diet_plan_id FROM users WHERE id = ?", Long.class, aliceId);
        assertThat(active).isNull();
    }

    @Test
    void active_endpoints_without_token_return_401() {
        ResponseEntity<String> get = rest.exchange(
                "/api/diet/active", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(get.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> post = rest.exchange(
                "/api/diet/active", HttpMethod.POST,
                new HttpEntity<>(Map.of("planId", 1L), jsonHeaders()), String.class);
        assertThat(post.getStatusCode().value()).isEqualTo(401);
    }

    // ── day endpoint ─────────────────────────────────────────────────────

    @Test
    void day_returns_empty_totals_when_no_diary_and_no_plan() {
        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("date").asText()).isEqualTo(FIXED_DATE.toString());
        assertThat(body.get("entries").size()).isZero();
        assertThat(body.get("totals").get("kcal").decimalValue().intValue()).isZero();
        assertThat(body.get("target").isNull()).isTrue();
        assertThat(body.get("remaining").isNull()).isTrue();
    }

    @Test
    void day_sums_diary_macros_correctly_by_unit() {
        // 200g chicken → 330 kcal, 62.0 g protein
        insertDiary(aliceId, chickenId, 200, "g", FIXED_DATE.atTime(8, 0));
        // 1 portion chicken → 165 kcal, 31.0 g protein
        insertDiary(aliceId, chickenId, 1, "portion", FIXED_DATE.atTime(13, 0));

        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("entries").size()).isEqualTo(2);
        // Oldest-first.
        assertThat(body.get("entries").get(0).get("amount").decimalValue().intValue()).isEqualTo(200);
        assertThat(body.get("entries").get(1).get("amount").decimalValue().intValue()).isEqualTo(1);

        assertThat(body.get("totals").get("kcal").decimalValue().doubleValue()).isEqualTo(495.00);
        assertThat(body.get("totals").get("proteinG").decimalValue().doubleValue()).isEqualTo(93.00);
    }

    @Test
    void day_target_comes_from_active_plan_and_remaining_subtracts_totals() {
        // Plan: 200g chicken + 150g rice → 525 kcal target.
        long planId = createPlan(aliceId, "Plan");
        long meal = createMeal(planId, 0, "All-day", null);
        addMealItem(meal, 0, chickenId, 200, "g");
        addMealItem(meal, 1, riceId, 150, "g");
        setActive(aliceToken, planId);

        // Today: ate just 100g chicken → 165 kcal totals.
        insertDiary(aliceId, chickenId, 100, "g", FIXED_DATE.atTime(9, 0));

        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("totals").get("kcal").decimalValue().doubleValue()).isEqualTo(165.00);
        assertThat(body.get("target").get("kcal").decimalValue().doubleValue()).isEqualTo(525.00);
        // remaining = 525 - 165 = 360
        assertThat(body.get("remaining").get("kcal").decimalValue().doubleValue()).isEqualTo(360.00);
    }

    @Test
    void day_remaining_can_go_negative_when_over_target() {
        // Plan target: 165 kcal (one item of 100g chicken).
        long planId = createPlan(aliceId, "Tiny plan");
        long meal = createMeal(planId, 0, "Meal", null);
        addMealItem(meal, 0, chickenId, 100, "g");
        setActive(aliceToken, planId);

        // Ate 300g chicken → 495 kcal.
        insertDiary(aliceId, chickenId, 300, "g", FIXED_DATE.atTime(9, 0));

        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("totals").get("kcal").decimalValue().doubleValue()).isEqualTo(495.00);
        assertThat(body.get("target").get("kcal").decimalValue().doubleValue()).isEqualTo(165.00);
        assertThat(body.get("remaining").get("kcal").decimalValue().doubleValue()).isEqualTo(-330.00);
    }

    @Test
    void day_respects_date_window_boundaries() {
        // One entry just before today, one inside, one just after.
        insertDiary(aliceId, chickenId, 100, "g", FIXED_DATE.minusDays(1).atTime(23, 59));
        insertDiary(aliceId, chickenId, 100, "g", FIXED_DATE.atTime(12, 0));
        insertDiary(aliceId, chickenId, 100, "g", FIXED_DATE.plusDays(1).atTime(0, 1));

        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("entries").size()).isEqualTo(1);
        assertThat(body.get("totals").get("kcal").decimalValue().doubleValue()).isEqualTo(165.00);
    }

    @Test
    void day_defaults_to_today_when_date_param_omitted() {
        insertDiary(aliceId, chickenId, 100, "g", FIXED_DATE.atTime(12, 0));

        ResponseEntity<String> response = rest.exchange(
                "/api/diet/day", HttpMethod.GET,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());
        assertThat(body.get("date").asText()).isEqualTo(FIXED_DATE.toString());
        assertThat(body.get("entries").size()).isEqualTo(1);
    }

    @Test
    void day_does_not_leak_other_users_entries() {
        insertDiary(bobId, chickenId, 500, "g", FIXED_DATE.atTime(8, 0));
        JsonNode body = json(getDay(aliceToken, FIXED_DATE).getBody());
        assertThat(body.get("entries").size()).isZero();
        assertThat(body.get("totals").get("kcal").decimalValue().intValue()).isZero();
    }

    @Test
    void day_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/day", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void day_rejects_bad_date_format_with_400() {
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/day?date=not-a-date", HttpMethod.GET,
                new HttpEntity<>(null, bearer(aliceToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
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

    private long foodIdByName(String name) {
        return jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND is_global = true", Long.class, name);
    }

    private long createPlan(long ownerId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO diet_plans (owner_id, name) VALUES (?, ?) RETURNING id",
                Long.class, ownerId, name);
    }

    private long createMeal(long planId, int position, String name, String timeHint) {
        return jdbc.queryForObject(
                "INSERT INTO diet_meals (plan_id, position, name, time_hint) " +
                        "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, planId, position, name, timeHint);
    }

    private void addMealItem(long mealId, int position, long foodId, double amount, String unit) {
        jdbc.update(
                "INSERT INTO meal_items (meal_id, food_id, amount, unit, position) " +
                        "VALUES (?, ?, ?, ?, ?)",
                mealId, foodId, amount, unit, position);
    }

    private void insertDiary(long userId, long foodId, double amount, String unit,
                             java.time.LocalDateTime eatenAt) {
        OffsetDateTime ts = eatenAt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        jdbc.update(
                "INSERT INTO diary_entries (user_id, food_id, amount, unit, eaten_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                userId, foodId, amount, unit, ts);
    }

    private ResponseEntity<String> getActive(String token) {
        return rest.exchange(
                "/api/diet/active", HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class);
    }

    private void setActive(String token, Long planId) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId);
        ResponseEntity<String> response = rest.exchange(
                "/api/diet/active", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> getDay(String token, LocalDate date) {
        return rest.exchange(
                "/api/diet/day?date={date}", HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class,
                Map.of("date", date.toString()));
    }

    /** Pins the service-layer clock to {@link #FIXED_DATE} (Wed 2026-05-27). */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    FIXED_DATE.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());
        }
    }
}
