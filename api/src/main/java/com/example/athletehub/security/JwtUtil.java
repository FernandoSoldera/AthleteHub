package com.example.athletehub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and verifies short-lived access JWTs. The token subject is the user
 * id; expiration is configurable. The signing key is HMAC-SHA over a secret
 * pulled from {@code app.jwt.secret} (env-driven). Refresh tokens are opaque
 * random strings managed by {@link com.example.athletehub.service.RefreshTokenService}.
 */
@Component
public class JwtUtil {

    /** Minimum 256-bit key for HS256 / longer for HS384/HS512. */
    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @PostConstruct
    void validateJwtSecret() {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_LENGTH_BYTES +
                    " bytes. Set the JWT_SECRET environment variable to a strong, random value.");
        }
    }

    public String generateAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
