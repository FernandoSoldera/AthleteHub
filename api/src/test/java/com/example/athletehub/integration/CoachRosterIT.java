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
 * End-to-end test of AH-072 — coach roster (GET /api/coach/athletes) +
 * athlete's own coach view (GET /api/me/coach). Covers the visibility
 * isolation (no leakage between coaches), the default + override status
 * filter, the optional flag filter (silently drops unknown values),
 * cursor pagination, and the athlete-side single-coach lookup.
 *
 * <p>Rows are seeded via JdbcTemplate so the test can set `flag` and
 * `status` values precisely without going through the invite/accept
 * lifecycle each time.
 */
class CoachRosterIT extends AbstractIntegrationTest {

    private static final String COACH_EMAIL = "coach@example.com";
    private static final String COACH2_EMAIL = "coach2@example.com";
    private static final String A1_EMAIL = "a1@example.com";
    private static final String A2_EMAIL = "a2@example.com";
    private static final String A3_EMAIL = "a3@example.com";
    private static final String A4_EMAIL = "a4@example.com";
    private static final String STRANGER_EMAIL = "stranger@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String coachToken;
    private String coach2Token;
    private String a1Token;
    private String strangerToken;

    private long coachId;
    private long coach2Id;
    private long a1Id;
    private long a2Id;
    private long a3Id;
    private long a4Id;
    private long strangerId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM coach_athlete");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(COACH_EMAIL, "Coach One", "coach.one");
        register(COACH2_EMAIL, "Coach Two", "coach.two");
        register(A1_EMAIL, "A1", "a.one");
        register(A2_EMAIL, "A2", "a.two");
        register(A3_EMAIL, "A3", "a.three");
        register(A4_EMAIL, "A4", "a.four");
        register(STRANGER_EMAIL, "Stranger", "stranger");

        coachId = idOf(COACH_EMAIL);
        coach2Id = idOf(COACH2_EMAIL);
        a1Id = idOf(A1_EMAIL);
        a2Id = idOf(A2_EMAIL);
        a3Id = idOf(A3_EMAIL);
        a4Id = idOf(A4_EMAIL);
        strangerId = idOf(STRANGER_EMAIL);

        coachToken = authToken(COACH_EMAIL);
        coach2Token = authToken(COACH2_EMAIL);
        a1Token = authToken(A1_EMAIL);
        strangerToken = authToken(STRANGER_EMAIL);
    }

    // ── roster ───────────────────────────────────────────────────────────

    @Test
    void roster_empty_when_no_athletes() {
        JsonNode body = json(roster(coachToken, null, null, null, 20).getBody());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void roster_returns_active_athletes_with_hydrated_dto() {
        // coach has 3 active athletes, 1 pending (not in default view), 1 ended.
        long active1 = insertRelationship(coachId, a1Id, "active", "on_track", 80);
        long active2 = insertRelationship(coachId, a2Id, "active", "attention", 55);
        long active3 = insertRelationship(coachId, a3Id, "active", null, null);
        insertRelationship(coachId, a4Id, "pending", null, null);
        insertRelationship(coachId, strangerId, "ended", null, null);

        JsonNode body = json(roster(coachToken, null, null, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(3);

        Set<Long> ids = new HashSet<>();
        for (JsonNode item : body.get("items")) {
            ids.add(item.get("id").asLong());
            // every item has the athlete hydrated.
            assertThat(item.get("athlete").get("handle").isNull()).isFalse();
        }
        assertThat(ids).containsExactlyInAnyOrder(active1, active2, active3);
    }

    @Test
    void roster_does_not_leak_other_coaches_athletes() {
        insertRelationship(coachId, a1Id, "active", null, null);
        insertRelationship(coach2Id, a2Id, "active", null, null);

        JsonNode body1 = json(roster(coachToken, null, null, null, 20).getBody());
        assertThat(body1.get("items").size()).isEqualTo(1);
        assertThat(body1.get("items").get(0).get("athlete").get("handle").asText())
                .isEqualTo("a.one");

        JsonNode body2 = json(roster(coach2Token, null, null, null, 20).getBody());
        assertThat(body2.get("items").size()).isEqualTo(1);
        assertThat(body2.get("items").get(0).get("athlete").get("handle").asText())
                .isEqualTo("a.two");
    }

    @Test
    void roster_filter_by_flag_narrows_results() {
        insertRelationship(coachId, a1Id, "active", "on_track", 90);
        insertRelationship(coachId, a2Id, "active", "attention", 60);
        insertRelationship(coachId, a3Id, "active", "risk", 30);

        JsonNode body = json(roster(coachToken, null, "attention", null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("flag").asText()).isEqualTo("attention");
    }

    @Test
    void roster_filter_by_unknown_flag_silently_drops_to_unfiltered() {
        insertRelationship(coachId, a1Id, "active", "on_track", 90);
        insertRelationship(coachId, a2Id, "active", "attention", 60);

        JsonNode body = json(roster(coachToken, null, "great", null, 20).getBody());
        // Unknown flag → service drops the filter → unfiltered active list.
        assertThat(body.get("items").size()).isEqualTo(2);
    }

    @Test
    void roster_status_override_lets_coach_see_pending_invitations() {
        insertRelationship(coachId, a1Id, "active", null, null);
        insertRelationship(coachId, a2Id, "pending", null, null);
        insertRelationship(coachId, a3Id, "ended", null, null);

        JsonNode pendingOnly = json(roster(coachToken, "pending", null, null, 20).getBody());
        assertThat(pendingOnly.get("items").size()).isEqualTo(1);
        assertThat(pendingOnly.get("items").get(0).get("status").asText()).isEqualTo("pending");

        JsonNode endedOnly = json(roster(coachToken, "ended", null, null, 20).getBody());
        assertThat(endedOnly.get("items").size()).isEqualTo(1);
        assertThat(endedOnly.get("items").get(0).get("status").asText()).isEqualTo("ended");
    }

    @Test
    void roster_unknown_status_falls_back_to_active() {
        insertRelationship(coachId, a1Id, "active", null, null);
        insertRelationship(coachId, a2Id, "pending", null, null);

        JsonNode body = json(roster(coachToken, "verified", null, null, 20).getBody());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("status").asText()).isEqualTo("active");
    }

    @Test
    void roster_paginates_with_cursor() {
        long r1 = insertRelationship(coachId, a1Id, "active", null, null);
        long r2 = insertRelationship(coachId, a2Id, "active", null, null);
        long r3 = insertRelationship(coachId, a3Id, "active", null, null);

        JsonNode page1 = json(roster(coachToken, null, null, null, 2).getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        // Newest first (id DESC).
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(r3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(r2);
        long cursor = Long.parseLong(page1.get("nextCursor").asText());

        JsonNode page2 = json(roster(coachToken, null, null, cursor, 2).getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(r1);
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void roster_returns_null_for_flag_and_adherence_when_unset() {
        insertRelationship(coachId, a1Id, "active", null, null);
        JsonNode item = json(roster(coachToken, null, null, null, 20).getBody())
                .get("items").get(0);
        assertThat(item.get("flag").isNull()).isTrue();
        assertThat(item.get("adherencePct").isNull()).isTrue();
    }

    @Test
    void roster_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/coach/athletes", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── my coach ─────────────────────────────────────────────────────────

    @Test
    void my_coach_returns_null_when_no_active_relationship() {
        ResponseEntity<String> response = myCoach(a1Token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isIn(null, "", "null");
    }

    @Test
    void my_coach_returns_active_relationship_with_coach_hydrated() {
        long relId = insertRelationship(coachId, a1Id, "active", null, null);

        JsonNode body = json(myCoach(a1Token).getBody());
        assertThat(body.get("id").asLong()).isEqualTo(relId);
        assertThat(body.get("status").asText()).isEqualTo("active");
        assertThat(body.get("coach").get("handle").asText()).isEqualTo("coach.one");
    }

    @Test
    void my_coach_excludes_pending_and_ended() {
        // a1 has a pending invite but no active coach.
        insertRelationship(coachId, a1Id, "pending", null, null);
        // a1 also has an ended past coach (use coach2 to satisfy UNIQUE).
        insertRelationship(coach2Id, a1Id, "ended", null, null);

        ResponseEntity<String> response = myCoach(a1Token);
        assertThat(response.getBody()).isIn(null, "", "null");
    }

    @Test
    void my_coach_does_not_leak_other_athletes_coach() {
        insertRelationship(coachId, a1Id, "active", null, null);

        // 'stranger' has no coach — should get null even though one exists
        // for someone else.
        ResponseEntity<String> response = myCoach(strangerToken);
        assertThat(response.getBody()).isIn(null, "", "null");
    }

    @Test
    void my_coach_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
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

    private long insertRelationship(long coachId, long athleteId, String status,
                                    String flag, Integer adherencePct) {
        return jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status, flag, adherence_pct) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, coachId, athleteId, status, flag, adherencePct);
    }

    private ResponseEntity<String> roster(String token, String status, String flag,
                                          Long cursor, int limit) {
        StringBuilder template = new StringBuilder("/api/coach/athletes?limit={limit}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("limit", limit);
        if (status != null) {
            template.append("&status={status}");
            vars.put("status", status);
        }
        if (flag != null) {
            template.append("&flag={flag}");
            vars.put("flag", flag);
        }
        if (cursor != null) {
            template.append("&cursor={cursor}");
            vars.put("cursor", cursor);
        }
        return rest.exchange(
                template.toString(), HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class, vars);
    }

    private ResponseEntity<String> myCoach(String token) {
        return rest.exchange(
                "/api/me/coach", HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class);
    }
}
