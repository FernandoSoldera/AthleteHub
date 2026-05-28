package com.example.athletehub.service;

import com.example.athletehub.model.RefreshToken;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Issues opaque refresh tokens. The plain token value is returned to the
 * caller at issuance and never stored; only its SHA-256 hash lives in the
 * database (so a DB compromise doesn't yield valid refresh tokens). Rotation
 * and reuse-detection arrive in AH-013.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 random bytes → 64-char hex token; ~256 bits of entropy. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-days:30}")
    private int refreshTokenExpirationDays;

    /**
     * Generate, persist, and return a fresh refresh token. The {@code plainValue}
     * is what the client should keep; the entity's {@code tokenHash} is what we
     * store and look up by.
     */
    @Transactional
    public IssuedRefreshToken issue(User user, String deviceInfo) {
        String plainValue = generateOpaqueToken();
        String tokenHash = sha256Hex(plainValue);

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(entity);

        return new IssuedRefreshToken(plainValue, entity);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** What {@link #issue} returns: the value to ship to the client + the persisted row. */
    public record IssuedRefreshToken(String plainValue, RefreshToken entity) {}
}
