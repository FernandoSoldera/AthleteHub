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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-081 — conversations + messages. Covers the lazy
 * open-for-relationship path, sending a message (with last-message
 * denorm bump + sender read-pointer auto-advance), reading messages
 * with cursor pagination, the visibility chokepoint (non-participant →
 * 404, same as not-found), unread counts (don't include the viewer's
 * own sends), the read-pointer advance endpoint, and 401-without-token.
 */
class MessagingIT extends AbstractIntegrationTest {

    private static final String COACH_EMAIL = "msg.coach@example.com";
    private static final String ATHLETE_EMAIL = "msg.athlete@example.com";
    private static final String STRANGER_EMAIL = "msg.stranger@example.com";
    private static final String PASSWORD = "supersecret1!";

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String coachToken;
    private String athleteToken;
    private String strangerToken;

    private long coachId;
    private long athleteId;
    @SuppressWarnings("unused") private long strangerId;
    private long relationshipId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversation_participants");
        jdbc.update("DELETE FROM conversations");
        jdbc.update("DELETE FROM coach_athlete");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(COACH_EMAIL, "Msg Coach", "msg.coach");
        register(ATHLETE_EMAIL, "Msg Athlete", "msg.athlete");
        register(STRANGER_EMAIL, "Msg Stranger", "msg.stranger");

        coachId = idOf(COACH_EMAIL);
        athleteId = idOf(ATHLETE_EMAIL);
        strangerId = idOf(STRANGER_EMAIL);
        coachToken = authToken(COACH_EMAIL);
        athleteToken = authToken(ATHLETE_EMAIL);
        strangerToken = authToken(STRANGER_EMAIL);

        relationshipId = jdbc.queryForObject(
                "INSERT INTO coach_athlete (coach_id, athlete_id, status) VALUES (?, ?, 'active') RETURNING id",
                Long.class, coachId, athleteId);
    }

    // ── open + send ──────────────────────────────────────────────────────

    @Test
    void open_for_relationship_creates_thread_lazily_and_returns_peer() {
        JsonNode dto = json(openForRelationship(coachToken, relationshipId).getBody());
        assertThat(dto.get("id").asLong()).isPositive();
        assertThat(dto.get("coachAthleteId").asLong()).isEqualTo(relationshipId);
        assertThat(dto.get("peer").get("handle").asText()).isEqualTo("msg.athlete");
        assertThat(dto.get("unreadCount").asLong()).isZero();
        assertThat(dto.get("lastMessageAt").isNull()).isTrue();

        // The row + both participants exist.
        long convoId = dto.get("id").asLong();
        Integer participants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_participants WHERE conversation_id = ?",
                Integer.class, convoId);
        assertThat(participants).isEqualTo(2);
    }

    @Test
    void open_for_relationship_idempotent() {
        long first = json(openForRelationship(coachToken, relationshipId).getBody())
                .get("id").asLong();
        long second = json(openForRelationship(athleteToken, relationshipId).getBody())
                .get("id").asLong();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void send_message_bumps_last_message_and_returns_201() {
        long convoId = openConvoId(coachToken, relationshipId);

        ResponseEntity<String> sent = sendMessage(coachToken, convoId, "Welcome to the program!");
        assertThat(sent.getStatusCode().value()).isEqualTo(201);
        JsonNode msg = json(sent.getBody());
        assertThat(msg.get("conversationId").asLong()).isEqualTo(convoId);
        assertThat(msg.get("senderId").asLong()).isEqualTo(coachId);
        assertThat(msg.get("body").asText()).isEqualTo("Welcome to the program!");

        // Inbox row shows the preview + timestamp.
        JsonNode convo = inboxFirstItem(coachToken);
        assertThat(convo.get("lastMessagePreview").asText())
                .isEqualTo("Welcome to the program!");
        assertThat(convo.get("lastMessageAt").isNull()).isFalse();
    }

    // ── visibility ───────────────────────────────────────────────────────

    @Test
    void non_participant_send_returns_404() {
        long convoId = openConvoId(coachToken, relationshipId);
        ResponseEntity<String> response = sendMessage(strangerToken, convoId, "hi");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void non_participant_list_returns_404() {
        long convoId = openConvoId(coachToken, relationshipId);
        ResponseEntity<String> response = rest.exchange(
                "/api/conversations/" + convoId + "/messages",
                HttpMethod.GET, new HttpEntity<>(null, bearer(strangerToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void open_for_relationship_as_non_participant_returns_404() {
        ResponseEntity<String> response = openForRelationship(strangerToken, relationshipId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // ── validation ───────────────────────────────────────────────────────

    @Test
    void send_rejects_empty_body_with_400() {
        long convoId = openConvoId(coachToken, relationshipId);
        ResponseEntity<String> response = sendMessage(coachToken, convoId, "");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void send_rejects_oversized_body_with_400() {
        long convoId = openConvoId(coachToken, relationshipId);
        String huge = "x".repeat(4001);
        ResponseEntity<String> response = sendMessage(coachToken, convoId, huge);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    // ── unread count ─────────────────────────────────────────────────────

    @Test
    void unread_count_skips_own_sends() {
        long convoId = openConvoId(coachToken, relationshipId);
        sendMessage(coachToken, convoId, "from coach 1");
        sendMessage(coachToken, convoId, "from coach 2");

        // Coach's own sends → coach sees 0 unread.
        JsonNode coachInboxItem = inboxFirstItem(coachToken);
        assertThat(coachInboxItem.get("unreadCount").asLong()).isZero();

        // Athlete sees 2.
        JsonNode athleteInboxItem = inboxFirstItem(athleteToken);
        assertThat(athleteInboxItem.get("unreadCount").asLong()).isEqualTo(2);
    }

    @Test
    void mark_read_clears_unread() {
        long convoId = openConvoId(coachToken, relationshipId);
        sendMessage(coachToken, convoId, "hi");
        sendMessage(coachToken, convoId, "another");

        ResponseEntity<String> response = rest.exchange(
                "/api/conversations/" + convoId + "/read", HttpMethod.POST,
                new HttpEntity<>(null, bearer(athleteToken)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        JsonNode inboxItem = inboxFirstItem(athleteToken);
        assertThat(inboxItem.get("unreadCount").asLong()).isZero();
    }

    // ── thread pagination ────────────────────────────────────────────────

    @Test
    void messages_list_paginates_with_cursor() {
        long convoId = openConvoId(coachToken, relationshipId);
        long m1 = sendAndId(coachToken, convoId, "one");
        long m2 = sendAndId(coachToken, convoId, "two");
        long m3 = sendAndId(coachToken, convoId, "three");

        JsonNode page1 = json(rest.exchange(
                "/api/conversations/" + convoId + "/messages?limit=2",
                HttpMethod.GET, new HttpEntity<>(null, bearer(coachToken)), String.class)
                .getBody());
        assertThat(page1.get("items").size()).isEqualTo(2);
        // Newest first.
        assertThat(page1.get("items").get(0).get("id").asLong()).isEqualTo(m3);
        assertThat(page1.get("items").get(1).get("id").asLong()).isEqualTo(m2);

        long cursor = Long.parseLong(page1.get("nextCursor").asText());
        JsonNode page2 = json(rest.exchange(
                "/api/conversations/" + convoId + "/messages?limit=2&cursor=" + cursor,
                HttpMethod.GET, new HttpEntity<>(null, bearer(coachToken)), String.class)
                .getBody());
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("id").asLong()).isEqualTo(m1);
    }

    // ── auth ─────────────────────────────────────────────────────────────

    @Test
    void endpoints_without_token_return_401() {
        ResponseEntity<String> inbox = rest.exchange(
                "/api/conversations", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(inbox.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> open = rest.exchange(
                "/api/me/coach-athletes/" + relationshipId + "/conversation",
                HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(open.getStatusCode().value()).isEqualTo(401);
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

    private ResponseEntity<String> openForRelationship(String token, long relId) {
        return rest.exchange(
                "/api/me/coach-athletes/" + relId + "/conversation",
                HttpMethod.POST, new HttpEntity<>(null, bearer(token)), String.class);
    }

    private long openConvoId(String token, long relId) {
        ResponseEntity<String> response = openForRelationship(token, relId);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> sendMessage(String token, long convoId, String body) {
        return rest.exchange(
                "/api/conversations/" + convoId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("body", body), bearer(token)), String.class);
    }

    private long sendAndId(String token, long convoId, String body) {
        ResponseEntity<String> response = sendMessage(token, convoId, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response.getBody()).get("id").asLong();
    }

    private JsonNode inboxFirstItem(String token) {
        JsonNode page = json(rest.exchange(
                "/api/conversations", HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)), String.class).getBody());
        assertThat(page.get("items").size()).isGreaterThan(0);
        return page.get("items").get(0);
    }
}
