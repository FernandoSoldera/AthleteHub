package com.example.athletehub.integration;

import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.enums.OAuthProvider;
import com.example.athletehub.exception.InvalidOAuthTokenException;
import com.example.athletehub.model.OAuthAccount;
import com.example.athletehub.repository.OAuthAccountRepository;
import com.example.athletehub.repository.RefreshTokenRepository;
import com.example.athletehub.repository.UserRepository;
import com.example.athletehub.service.AuthService;
import com.example.athletehub.service.OAuthTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of AH-015 social login. The actual ID-token verifier is
 * replaced with a Mockito mock, so we don't need WireMock + a stub JWKS just
 * to exercise the link/create logic.
 */
class SocialAuthIT extends AbstractIntegrationTest {

    @MockitoBean
    OAuthTokenVerifier oauthTokenVerifier;

    @Autowired UserRepository userRepository;
    @Autowired OAuthAccountRepository oauthAccountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuthService authService;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        oauthAccountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── new user via Google ────────────────────────────────────────────────

    @Test
    void google_login_for_unknown_email_creates_user_links_oauth_account_and_returns_tokens() {
        when(oauthTokenVerifier.verify(eq(OAuthProvider.GOOGLE), any()))
                .thenReturn(new OAuthTokenVerifier.OAuthIdentity(
                        "google-uid-1", "Newuser@example.com", "New User"));

        ResponseEntity<String> response = oauth("google", "any-token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("user").get("email").asText()).isEqualTo("newuser@example.com");
        assertThat(body.get("user").get("fullName").asText()).isEqualTo("New User");
        // Handle derived from email local part.
        assertThat(body.get("user").get("handle").asText()).isEqualTo("newuser");

        // Persisted user + oauth_accounts link.
        var user = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNull();   // OAuth-only — no local password
        Optional<OAuthAccount> link = oauthAccountRepository
                .findByProviderAndProviderUid(OAuthProvider.GOOGLE, "google-uid-1");
        assertThat(link).isPresent();
        assertThat(link.get().getUser().getId()).isEqualTo(user.getId());
    }

    // ── repeat Google login (existing oauth_account) ───────────────────────

    @Test
    void second_google_login_reuses_the_same_user_no_new_link_row() {
        when(oauthTokenVerifier.verify(eq(OAuthProvider.GOOGLE), any()))
                .thenReturn(new OAuthTokenVerifier.OAuthIdentity(
                        "google-uid-2", "repeat@example.com", "Repeat User"));

        oauth("google", "first-token");
        oauth("google", "second-token");

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(oauthAccountRepository.count()).isEqualTo(1);
    }

    // ── existing email user (password signup) gets linked ─────────────────

    @Test
    void google_login_for_known_email_links_the_existing_user() {
        authService.register(SignupRequest.builder()
                .email("alex@example.com")
                .password("supersecret1!")
                .fullName("Alex Carter")
                .handle("alex.lifts")
                .build());

        when(oauthTokenVerifier.verify(eq(OAuthProvider.GOOGLE), any()))
                .thenReturn(new OAuthTokenVerifier.OAuthIdentity(
                        "google-uid-3", "Alex@example.com", "Alex Carter"));

        ResponseEntity<String> response = oauth("google", "any-token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Still exactly one user — we linked, not created.
        assertThat(userRepository.count()).isEqualTo(1);
        // OAuth-account link points at the existing user. (Comparing ids dodges
        // a LazyInitializationException — User is fetched lazily and we're out
        // of the Hibernate session here.)
        var alex = userRepository.findByEmail("alex@example.com").orElseThrow();
        Optional<OAuthAccount> link = oauthAccountRepository
                .findByProviderAndProviderUid(OAuthProvider.GOOGLE, "google-uid-3");
        assertThat(link).isPresent();
        assertThat(link.get().getUser().getId()).isEqualTo(alex.getId());
        // Handle preserved from the original signup.
        assertThat(json(response.getBody()).get("user").get("handle").asText()).isEqualTo("alex.lifts");
    }

    // ── invalid token from verifier surfaces as 401 ────────────────────────

    @Test
    void invalid_id_token_returns_401() {
        when(oauthTokenVerifier.verify(any(), any()))
                .thenThrow(new InvalidOAuthTokenException());

        ResponseEntity<String> response = oauth("google", "garbage");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText()).isEqualTo("INVALID_OAUTH_TOKEN");
    }

    // ── unsupported provider name ─────────────────────────────────────────

    @Test
    void unsupported_provider_in_path_returns_401() {
        ResponseEntity<String> response = oauth("microsoft", "any-token");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response.getBody()).get("code").asText()).isEqualTo("INVALID_OAUTH_TOKEN");
    }

    // ── handle-collision: derived handle already in use ───────────────────

    @Test
    void handle_collision_appends_numeric_suffix() {
        // Pre-claim the "newuser" handle via password signup.
        authService.register(SignupRequest.builder()
                .email("squatter@example.com")
                .password("supersecret1!")
                .fullName("Squatter")
                .handle("newuser")
                .build());

        when(oauthTokenVerifier.verify(eq(OAuthProvider.GOOGLE), any()))
                .thenReturn(new OAuthTokenVerifier.OAuthIdentity(
                        "google-uid-4", "newuser@example.com", "New User"));

        ResponseEntity<String> response = oauth("google", "any-token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // The derived "newuser" was taken, so the next free candidate is "newuser_1".
        assertThat(json(response.getBody()).get("user").get("handle").asText())
                .isEqualTo("newuser_1");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<String> oauth(String providerName, String idToken) {
        return rest.postForEntity(
                "/api/auth/oauth/" + providerName,
                new HttpEntity<>(Map.of("idToken", idToken), jsonHeaders()),
                String.class);
    }
}
