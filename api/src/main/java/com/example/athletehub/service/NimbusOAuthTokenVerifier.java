package com.example.athletehub.service;

import com.example.athletehub.enums.OAuthProvider;
import com.example.athletehub.exception.InvalidOAuthTokenException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Production {@link OAuthTokenVerifier}. Builds one {@link NimbusJwtDecoder}
 * per provider against the provider's JWKS endpoint (lazy fetch + cache), and
 * validates issuer + audience after decoding. Audience is the OAuth client id
 * the mobile app uses for the provider.
 */
@Component
@Slf4j
public class NimbusOAuthTokenVerifier implements OAuthTokenVerifier {

    private final Map<OAuthProvider, ProviderConfig> configs = new EnumMap<>(OAuthProvider.class);

    @Value("${app.oauth.google.audience:disabled}") private String googleAudience;
    @Value("${app.oauth.google.issuer:https://accounts.google.com}") private String googleIssuer;
    @Value("${app.oauth.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") private String googleJwksUri;

    @Value("${app.oauth.apple.audience:disabled}") private String appleAudience;
    @Value("${app.oauth.apple.issuer:https://appleid.apple.com}") private String appleIssuer;
    @Value("${app.oauth.apple.jwks-uri:https://appleid.apple.com/auth/keys}") private String appleJwksUri;

    @PostConstruct
    void init() {
        configs.put(OAuthProvider.GOOGLE, new ProviderConfig(
                NimbusJwtDecoder.withJwkSetUri(googleJwksUri).build(),
                googleIssuer, googleAudience));
        configs.put(OAuthProvider.APPLE, new ProviderConfig(
                NimbusJwtDecoder.withJwkSetUri(appleJwksUri).build(),
                appleIssuer, appleAudience));
    }

    @Override
    public OAuthIdentity verify(OAuthProvider provider, String idToken) {
        ProviderConfig cfg = configs.get(provider);
        if (cfg == null) throw new InvalidOAuthTokenException("Unsupported provider: " + provider);

        Jwt jwt;
        try {
            jwt = cfg.decoder.decode(idToken);
        } catch (JwtException ex) {
            log.debug("OAuth token decode failed for {}: {}", provider, ex.getMessage());
            throw new InvalidOAuthTokenException();
        }

        // Issuer
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!cfg.expectedIssuer.equals(issuer)) {
            throw new InvalidOAuthTokenException();
        }

        // Audience — provider may put a String or a List<String> in `aud`.
        List<String> audiences = jwt.getAudience();
        if (audiences == null || !audiences.contains(cfg.expectedAudience)) {
            throw new InvalidOAuthTokenException();
        }

        String providerUid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (providerUid == null || email == null) {
            throw new InvalidOAuthTokenException();
        }
        return new OAuthIdentity(providerUid, email, name);
    }

    private record ProviderConfig(JwtDecoder decoder, String expectedIssuer, String expectedAudience) {}
}
