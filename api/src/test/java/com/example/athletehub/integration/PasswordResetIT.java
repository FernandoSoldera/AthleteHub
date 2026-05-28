package com.example.athletehub.integration;

import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.model.PasswordResetToken;
import com.example.athletehub.repository.PasswordResetTokenRepository;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.service.PasswordResetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of AH-014 password-reset flow. Spins the full context up,
 * registers a user, asks for a reset, <em>reads the code from the GreenMail
 * mailbox</em>, sets a new password, and verifies the new password works
 * (and the old one doesn't). Also exercises the no-enumeration property, the
 * single-use rule, and expiry.
 */
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String EMAIL = "alex@example.com";
    private static final String OLD_PASSWORD = "supersecret1!";
    private static final String NEW_PASSWORD = "brand-new-pw-2!";
    private static final String HANDLE = "alex.lifts";

    /** Matches the 6-char hex code our email template embeds. */
    private static final Pattern CODE_PATTERN = Pattern.compile("reset code is:\\s+([0-9A-F]{6})");

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void registerKnownUser() {
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();

        register(Map.of(
                "email", EMAIL,
                "password", OLD_PASSWORD,
                "fullName", "Alex Carter",
                "handle", HANDLE));
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void forgot_then_reset_with_code_from_email_changes_the_password() {
        ResponseEntity<String> forgotResponse = forgot(EMAIL);
        assertThat(forgotResponse.getStatusCode().value()).isEqualTo(202);

        String code = waitForResetCodeEmail(EMAIL);
        assertThat(code).matches("[0-9A-F]{6}");

        ResponseEntity<String> resetResponse = reset(code, NEW_PASSWORD);
        assertThat(resetResponse.getStatusCode().value()).isEqualTo(204);

        // Old password no longer works.
        assertThat(login(EMAIL, OLD_PASSWORD).getStatusCode().value()).isEqualTo(401);
        // New password does.
        assertThat(login(EMAIL, NEW_PASSWORD).getStatusCode().value()).isEqualTo(200);
    }

    // ── no enumeration ─────────────────────────────────────────────────────

    @Test
    void forgot_with_unknown_email_still_returns_202_and_sends_no_mail() {
        ResponseEntity<String> response = forgot("nobody@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    // ── single-use ─────────────────────────────────────────────────────────

    @Test
    void reset_with_the_same_code_a_second_time_is_rejected_400() {
        forgot(EMAIL);
        String code = waitForResetCodeEmail(EMAIL);

        assertThat(reset(code, NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> second = reset(code, "yet-another-pw1!");
        assertThat(second.getStatusCode().value()).isEqualTo(400);
        assertThat(json(second.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_RESET_CODE.name());
    }

    // ── unknown code ───────────────────────────────────────────────────────

    @Test
    void reset_with_unknown_code_is_rejected_400() {
        ResponseEntity<String> response = reset("ZZZZZZ", NEW_PASSWORD);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_RESET_CODE.name());
    }

    // ── expiry ─────────────────────────────────────────────────────────────

    @Test
    void reset_with_expired_code_is_rejected_400() {
        forgot(EMAIL);
        String code = waitForResetCodeEmail(EMAIL);

        // Backdate the token's expiry directly.
        PasswordResetToken row = passwordResetTokenRepository
                .findByTokenHash(PasswordResetService.sha256Hex(code)).orElseThrow();
        row.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(row);

        ResponseEntity<String> response = reset(code, NEW_PASSWORD);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).get("code").asText())
                .isEqualTo(MessageCode.INVALID_RESET_CODE.name());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** Wait briefly for an email to land in GreenMail and return the embedded code. */
    private String waitForResetCodeEmail(String recipient) {
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(GREEN_MAIL.getReceivedMessages()).isNotEmpty());
        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        String body = GreenMailUtil.getBody(messages[0]);
        Matcher matcher = CODE_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("No reset code found in email body to " + recipient + ":\n" + body);
        }
        return matcher.group(1);
    }

    private ResponseEntity<String> register(Map<String, Object> body) {
        return rest.postForEntity("/api/auth/register", new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> forgot(String email) {
        return rest.postForEntity(
                "/api/auth/password/forgot",
                new HttpEntity<>(Map.of("email", email), jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> reset(String code, String password) {
        return rest.postForEntity(
                "/api/auth/password/reset",
                new HttpEntity<>(Map.of("code", code, "password", password), jsonHeaders()),
                String.class);
    }

    // Silence unused-import warnings if any IDE flags JsonNode for the helper return type.
    @SuppressWarnings("unused")
    private static final Class<?> KEEP_JSON_NODE = JsonNode.class;
}
