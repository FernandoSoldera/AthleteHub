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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-071 — coach↔athlete invite + consent. Covers
 * the coach happy path (new + ended-revive), dedup matrix (pending and
 * active → 409), self-invite + unknown-handle rejections, the athlete
 * inbox (pending only, with hydrated coach side), accept / decline
 * state machine, and the ownership / state guards.
 */
class CoachInvitesIT extends AbstractIntegrationTest {

    private static final String COACH_EMAIL = "coach@example.com";
    private static final String ATHLETE_EMAIL = "athlete@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String coachToken;
    private String athleteToken;
    private String otherToken;
    private long coachId;
    private long athleteId;
    private long otherId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM coach_athlete");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(COACH_EMAIL, "Coach Carter", "coach.carter");
        register(ATHLETE_EMAIL, "Alex Athlete", "alex.athlete");
        register(OTHER_EMAIL, "Other O.", "other.o");
        coachId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, COACH_EMAIL);
        athleteId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ATHLETE_EMAIL);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, OTHER_EMAIL);
        coachToken = authToken(COACH_EMAIL);
        athleteToken = authToken(ATHLETE_EMAIL);
        otherToken = authToken(OTHER_EMAIL);
    }

    // ── invite ───────────────────────────────────────────────────────────

    @Test
    void invite_creates_pending_row_with_both_sides_hydrated() {
        ResponseEntity<String> response = invite(coachToken, "alex.athlete");
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("status").asText()).isEqualTo("pending");
        assertThat(dto.get("since").isNull()).isTrue();
        assertThat(dto.get("coach").get("handle").asText()).isEqualTo("coach.carter");
        assertThat(dto.get("athlete").get("handle").asText()).isEqualTo("alex.athlete");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coach_athlete WHERE coach_id = ? AND athlete_id = ?",
                Integer.class, coachId, athleteId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invite_unknown_handle_returns_404() {
        ResponseEntity<String> response = invite(coachToken, "ghost.user");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void invite_self_returns_400() {
        ResponseEntity<String> response = invite(coachToken, "coach.carter");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void invite_handle_lookup_is_case_insensitive() {
        ResponseEntity<String> response = invite(coachToken, "ALEX.ATHLETE");
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void invite_when_pending_already_exists_returns_409() {
        invite(coachToken, "alex.athlete");
        ResponseEntity<String> dup = invite(coachToken, "alex.athlete");
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(json(dup.getBody()).get("code").asText())
                .isEqualTo(MessageCode.COACH_LINK_EXISTS.name());
    }

    @Test
    void invite_when_active_already_exists_returns_409() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        accept(athleteToken, inviteId);

        ResponseEntity<String> dup = invite(coachToken, "alex.athlete");
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(json(dup.getBody()).get("code").asText())
                .isEqualTo(MessageCode.COACH_LINK_EXISTS.name());
    }

    @Test
    void invite_when_ended_revives_the_same_row_back_to_pending() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        decline(athleteToken, inviteId); // ended

        ResponseEntity<String> revived = invite(coachToken, "alex.athlete");
        assertThat(revived.getStatusCode().value()).isEqualTo(201);
        JsonNode dto = json(revived.getBody());
        assertThat(dto.get("id").asLong()).isEqualTo(inviteId); // same row, not a new one
        assertThat(dto.get("status").asText()).isEqualTo("pending");

        // Still one row in the DB for this pair.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coach_athlete WHERE coach_id = ? AND athlete_id = ?",
                Integer.class, coachId, athleteId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invite_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/coach/invites", HttpMethod.POST,
                new HttpEntity<>(Map.of("handle", "alex.athlete"), jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── athlete inbox ────────────────────────────────────────────────────

    @Test
    void inbox_returns_pending_invites_with_coach_hydrated() {
        invite(coachToken, "alex.athlete");

        JsonNode body = json(listInbox(athleteToken).getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("status").asText()).isEqualTo("pending");
        assertThat(body.get(0).get("coach").get("handle").asText()).isEqualTo("coach.carter");
    }

    @Test
    void inbox_excludes_active_and_ended() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        accept(athleteToken, inviteId);  // now active

        // Send another that gets declined → ends up `ended`.
        // (Re-using same pair would conflict, so seed via JDBC.)
        jdbc.update(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) VALUES (?, ?, 'ended')",
                otherId, athleteId);

        JsonNode body = json(listInbox(athleteToken).getBody());
        assertThat(body.size()).isZero();
    }

    @Test
    void inbox_excludes_outgoing_invites_where_caller_is_coach() {
        invite(coachToken, "alex.athlete");
        // Coach's inbox is empty — they sent the invite, didn't receive one.
        JsonNode body = json(listInbox(coachToken).getBody());
        assertThat(body.size()).isZero();
    }

    @Test
    void inbox_without_token_returns_401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ── accept ───────────────────────────────────────────────────────────

    @Test
    void accept_flips_status_to_active_and_sets_since() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();

        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/accept", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        JsonNode dto = json(response.getBody());
        assertThat(dto.get("status").asText()).isEqualTo("active");
        assertThat(dto.get("since").isNull()).isFalse();
    }

    @Test
    void accept_by_non_target_returns_404() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();

        // 'other' user has no business accepting alex's invite.
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/accept", HttpMethod.POST,
                new HttpEntity<>(null, bearer(otherToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVITE_NOT_FOUND.name());
    }

    @Test
    void accept_unknown_returns_404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/999999/accept", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void accept_when_already_active_returns_409() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        accept(athleteToken, inviteId);

        ResponseEntity<String> second = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/accept", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVITE_NOT_PENDING.name());
    }

    // ── decline ──────────────────────────────────────────────────────────

    @Test
    void decline_flips_status_to_ended() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();

        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/decline", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json(response.getBody()).get("status").asText()).isEqualTo("ended");
    }

    @Test
    void decline_by_non_target_returns_404() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/decline", HttpMethod.POST,
                new HttpEntity<>(null, bearer(otherToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void decline_when_already_active_returns_409() {
        long inviteId = json(invite(coachToken, "alex.athlete").getBody()).get("id").asLong();
        accept(athleteToken, inviteId);

        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/decline", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVITE_NOT_PENDING.name());
    }

    @Test
    void accept_decline_without_token_returns_401() {
        ResponseEntity<String> accept = rest.exchange(
                "/api/me/coach-invites/1/accept", HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(accept.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> decline = rest.exchange(
                "/api/me/coach-invites/1/decline", HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(decline.getStatusCode().value()).isEqualTo(401);
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

    private ResponseEntity<String> invite(String token, String handle) {
        return rest.exchange(
                "/api/coach/invites", HttpMethod.POST,
                new HttpEntity<>(Map.of("handle", handle), bearer(token)), String.class);
    }

    private ResponseEntity<String> listInbox(String token) {
        return rest.exchange(
                "/api/me/coach-invites", HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class);
    }

    private void accept(String token, long inviteId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/accept", HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private void decline(String token, long inviteId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/coach-invites/" + inviteId + "/decline", HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
