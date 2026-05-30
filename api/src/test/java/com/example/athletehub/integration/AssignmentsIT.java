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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-074 — assignments CRUD. Covers coach create,
 * update, delete, the per-athlete list view, the athlete self-list,
 * the visibility matrix (non-coach / pending / ended / wrong-coach →
 * 404), bean validation (type enum, ref XOR), and the optional
 * status / scheduledOn filters.
 */
class AssignmentsIT extends AbstractIntegrationTest {

    private static final String COACH_EMAIL = "coach@example.com";
    private static final String OTHER_COACH_EMAIL = "coach2@example.com";
    private static final String ATHLETE_EMAIL = "athlete@example.com";
    private static final String STRANGER_EMAIL = "stranger@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String coachToken;
    private String otherCoachToken;
    private String athleteToken;
    private String strangerToken;

    private long coachId;
    private long otherCoachId;
    private long athleteId;
    private long strangerId;
    private long relationshipId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM assignments");
        jdbc.update("DELETE FROM coach_athlete");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(COACH_EMAIL, "Coach One", "coach.one");
        register(OTHER_COACH_EMAIL, "Coach Two", "coach.two");
        register(ATHLETE_EMAIL, "Athlete", "alex.athlete");
        register(STRANGER_EMAIL, "Stranger", "stranger");

        coachId = idOf(COACH_EMAIL);
        otherCoachId = idOf(OTHER_COACH_EMAIL);
        athleteId = idOf(ATHLETE_EMAIL);
        strangerId = idOf(STRANGER_EMAIL);
        coachToken = authToken(COACH_EMAIL);
        otherCoachToken = authToken(OTHER_COACH_EMAIL);
        athleteToken = authToken(ATHLETE_EMAIL);
        strangerToken = authToken(STRANGER_EMAIL);

        // Active relationship coach → athlete.
        relationshipId = jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) VALUES (?, ?, 'active') RETURNING id",
                Long.class, coachId, athleteId);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_assignment_returns_201_with_defaults() {
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "workout",
                "notes", "Push day, hit 100kg bench"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("type").asText()).isEqualTo("workout");
        assertThat(dto.get("status").asText()).isEqualTo("scheduled");
        assertThat(dto.get("relationshipId").asLong()).isEqualTo(relationshipId);
        assertThat(dto.get("refType").isNull()).isTrue();
        assertThat(dto.get("refId").isNull()).isTrue();
        assertThat(dto.get("notes").asText()).isEqualTo("Push day, hit 100kg bench");
    }

    @Test
    void create_accepts_full_ref_pair() {
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "workout",
                "refType", "workout_template",
                "refId", 42,
                "scheduledFor", "2026-06-01"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("refType").asText()).isEqualTo("workout_template");
        assertThat(dto.get("refId").asLong()).isEqualTo(42);
        assertThat(dto.get("scheduledFor").asText()).isEqualTo("2026-06-01");
    }

    @Test
    void create_rejects_half_ref_pair_with_400() {
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "workout",
                "refType", "workout_template"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_rejects_unknown_type() {
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "freestyle"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.VALIDATION_FAILED.name());
    }

    @Test
    void create_rejects_unknown_ref_type() {
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "workout",
                "refType", "playlist",
                "refId", 7));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_for_athlete_with_no_relationship_returns_404() {
        ResponseEntity<String> response = createAssignment(coachToken, strangerId, Map.of(
                "type", "workout"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void create_by_non_coach_returns_404() {
        // Other coach has no relationship with our athlete.
        ResponseEntity<String> response = createAssignment(otherCoachToken, athleteId, Map.of(
                "type", "workout"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void create_when_relationship_is_pending_returns_404() {
        // Move relationship to pending.
        jdbc.update("UPDATE coach_athlete SET status = 'pending' WHERE id = ?", relationshipId);
        ResponseEntity<String> response = createAssignment(coachToken, athleteId, Map.of(
                "type", "workout"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void create_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/coach/athletes/" + athleteId + "/assignments", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "workout"), jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_patches_status_scheduled_and_notes() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        ResponseEntity<String> response = patchAssignment(coachToken, assignmentId, Map.of(
                "status", "done",
                "scheduledFor", "2026-06-15",
                "notes", "Smashed it"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("status").asText()).isEqualTo("done");
        assertThat(dto.get("scheduledFor").asText()).isEqualTo("2026-06-15");
        assertThat(dto.get("notes").asText()).isEqualTo("Smashed it");
    }

    @Test
    void update_partial_only_changes_fields_present() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of(
                "type", "workout", "notes", "Original"));

        // Patch only status — notes should stay.
        ResponseEntity<String> response = patchAssignment(coachToken, assignmentId, Map.of(
                "status", "today"));
        JsonNode dto = json(response.getBody());
        assertThat(dto.get("status").asText()).isEqualTo("today");
        assertThat(dto.get("notes").asText()).isEqualTo("Original");
    }

    @Test
    void update_rejects_unknown_status() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        ResponseEntity<String> response = patchAssignment(coachToken, assignmentId, Map.of(
                "status", "victorious"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void update_by_other_coach_returns_404() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        ResponseEntity<String> response = patchAssignment(otherCoachToken, assignmentId, Map.of(
                "status", "done"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.ASSIGNMENT_NOT_FOUND.name());
    }

    @Test
    void update_unknown_returns_404() {
        ResponseEntity<String> response = patchAssignment(coachToken, 999_999L, Map.of(
                "status", "done"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_removes_the_row() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        ResponseEntity<String> response = rest.exchange(
                "/api/coach/assignments/" + assignmentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(coachToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE id = ?",
                Integer.class, assignmentId);
        assertThat(remaining).isZero();
    }

    @Test
    void delete_by_non_coach_returns_404() {
        long assignmentId = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        ResponseEntity<String> response = rest.exchange(
                "/api/coach/assignments/" + assignmentId, HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(otherCoachToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── list for athlete (coach view) ────────────────────────────────────

    @Test
    void list_for_athlete_returns_only_that_relationships_assignments() {
        createAndId(coachToken, athleteId, Map.of("type", "workout"));
        createAndId(coachToken, athleteId, Map.of("type", "diet"));

        // Seed an unrelated assignment for the OTHER coach via JdbcTemplate.
        long otherRel = jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) VALUES (?, ?, 'active') RETURNING id",
                Long.class, otherCoachId, strangerId);
        jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout')",
                otherRel);

        JsonNode body = json(listForAthlete(coachToken, athleteId, null, null).getBody());
        assertThat(body.get("items").size()).isEqualTo(2);
        // Both assignments link back to our relationship id.
        for (JsonNode item : body.get("items")) {
            assertThat(item.get("relationshipId").asLong()).isEqualTo(relationshipId);
        }
    }

    @Test
    void list_for_athlete_filters_by_status() {
        long a1 = createAndId(coachToken, athleteId, Map.of("type", "workout"));
        createAndId(coachToken, athleteId, Map.of("type", "workout"));
        patchAssignment(coachToken, a1, Map.of("status", "done"));

        JsonNode doneOnly = json(listForAthlete(coachToken, athleteId, "done", null).getBody());
        assertThat(doneOnly.get("items").size()).isEqualTo(1);
        assertThat(doneOnly.get("items").get(0).get("id").asLong()).isEqualTo(a1);
    }

    @Test
    void list_for_athlete_filters_by_scheduled_on() {
        createAndId(coachToken, athleteId, Map.of(
                "type", "workout", "scheduledFor", "2026-06-01"));
        createAndId(coachToken, athleteId, Map.of(
                "type", "workout", "scheduledFor", "2026-06-15"));

        JsonNode juneFirst = json(listForAthlete(coachToken, athleteId, null,
                LocalDate.of(2026, 6, 1)).getBody());
        assertThat(juneFirst.get("items").size()).isEqualTo(1);
        assertThat(juneFirst.get("items").get(0).get("scheduledFor").asText())
                .isEqualTo("2026-06-01");
    }

    @Test
    void list_for_athlete_by_non_coach_returns_404() {
        createAndId(coachToken, athleteId, Map.of("type", "workout"));
        ResponseEntity<String> response = listForAthlete(otherCoachToken, athleteId, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── list mine (athlete view) ─────────────────────────────────────────

    @Test
    void list_mine_returns_athletes_assignments_across_relationships() {
        createAndId(coachToken, athleteId, Map.of("type", "workout"));
        createAndId(coachToken, athleteId, Map.of("type", "diet"));

        JsonNode body = json(listMine(athleteToken, null, null).getBody());
        assertThat(body.get("items").size()).isEqualTo(2);
    }

    @Test
    void list_mine_returns_empty_when_no_active_relationship() {
        // Stranger has no coach.
        JsonNode body = json(listMine(strangerToken, null, null).getBody());
        assertThat(body.get("items").size()).isZero();
    }

    @Test
    void list_mine_does_not_leak_other_athletes_assignments() {
        // Coach also coaches stranger; stranger has an assignment.
        long otherRel = jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) VALUES (?, ?, 'active') RETURNING id",
                Long.class, coachId, strangerId);
        jdbc.update(
                "INSERT INTO assignments (coach_athlete_id, type) VALUES (?, 'workout')",
                otherRel);

        JsonNode body = json(listMine(athleteToken, null, null).getBody());
        assertThat(body.get("items").size()).isZero();
    }

    @Test
    void list_mine_filters_by_status() {
        long a1 = createAndId(coachToken, athleteId, Map.of("type", "workout"));
        createAndId(coachToken, athleteId, Map.of("type", "diet"));
        patchAssignment(coachToken, a1, Map.of("status", "done"));

        JsonNode doneOnly = json(listMine(athleteToken, "done", null).getBody());
        assertThat(doneOnly.get("items").size()).isEqualTo(1);
        assertThat(doneOnly.get("items").get(0).get("id").asLong()).isEqualTo(a1);
    }

    @Test
    void list_mine_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/assignments", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── pagination ───────────────────────────────────────────────────────

    @Test
    void list_paginates_with_cursor() {
        long a1 = createAndId(coachToken, athleteId, Map.of("type", "workout"));
        long a2 = createAndId(coachToken, athleteId, Map.of("type", "workout"));
        long a3 = createAndId(coachToken, athleteId, Map.of("type", "workout"));

        JsonNode page1 = json(rest.exchange(
                "/api/coach/athletes/" + athleteId + "/assignments?limit=2",
                HttpMethod.GET, new HttpEntity<>(null, bearer(coachToken)),
                String.class).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        // Newest first (id DESC).
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(a3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(a2);
        long cursor = Long.parseLong(page1.get("nextCursor").asText());

        JsonNode page2 = json(rest.exchange(
                "/api/coach/athletes/" + athleteId + "/assignments?limit=2&cursor=" + cursor,
                HttpMethod.GET, new HttpEntity<>(null, bearer(coachToken)),
                String.class).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(a1);
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

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
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

    private ResponseEntity<String> createAssignment(String token, long athleteId, Map<String, Object> body) {
        return rest.exchange(
                "/api/coach/athletes/" + athleteId + "/assignments", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), String.class);
    }

    private long createAndId(String token, long athleteId, Map<String, Object> body) {
        ResponseEntity<String> response = createAssignment(token, athleteId, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> patchAssignment(String token, long id, Map<String, Object> body) {
        return rest.exchange(
                "/api/coach/assignments/" + id, HttpMethod.PATCH,
                new HttpEntity<>(body, bearer(token)), String.class);
    }

    private ResponseEntity<String> listForAthlete(String token, long athleteId,
                                                  String status, LocalDate scheduledOn) {
        StringBuilder template =
                new StringBuilder("/api/coach/athletes/" + athleteId + "/assignments?limit=50");
        Map<String, Object> vars = new HashMap<>();
        if (status != null) {
            template.append("&status={status}");
            vars.put("status", status);
        }
        if (scheduledOn != null) {
            template.append("&scheduledOn={scheduledOn}");
            vars.put("scheduledOn", scheduledOn.toString());
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }

    private ResponseEntity<String> listMine(String token, String status, LocalDate scheduledOn) {
        StringBuilder template = new StringBuilder("/api/me/assignments?limit=50");
        Map<String, Object> vars = new HashMap<>();
        if (status != null) {
            template.append("&status={status}");
            vars.put("status", status);
        }
        if (scheduledOn != null) {
            template.append("&scheduledOn={scheduledOn}");
            vars.put("scheduledOn", scheduledOn.toString());
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }
}
