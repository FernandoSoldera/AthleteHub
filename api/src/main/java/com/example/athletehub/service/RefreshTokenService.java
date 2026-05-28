package com.example.athletehub.service;

import com.example.athletehub.exception.InvalidRefreshTokenException;
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
import java.util.List;

/**
 * Issues, rotates, and revokes opaque refresh tokens. The plain value is only
 * ever returned to the client at issuance; we store its SHA-256 hash so a DB
 * compromise doesn't yield valid refresh tokens.
 *
 * <p><b>Rotation policy (AH-013).</b> Every successful refresh:
 * <ol>
 *   <li>looks up the presented token by hash,</li>
 *   <li>rejects unknown / expired / already-revoked tokens with 401,</li>
 *   <li>marks the presented token <em>revoked</em>, and</li>
 *   <li>issues a brand-new refresh token (plus a new access token at the
 *       caller).</li>
 * </ol>
 *
 * <p><b>Reuse detection.</b> If a revoked refresh token is re-presented (the
 * legitimate client already rotated it, so its appearance again means an
 * attacker copied it), we revoke <em>every</em> currently-active refresh token
 * for that user before rejecting — the legitimate client is forced to log in
 * again, and any attacker tokens are killed at the same time.
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

    /** Generate, persist, and return a fresh refresh token. */
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

    /**
     * Validate the presented refresh token, revoke it, and issue a new one in
     * its place (token rotation). Throws {@link InvalidRefreshTokenException}
     * if the token is unknown, expired, or already revoked — and in the
     * already-revoked case, also revokes every other active token for the
     * user (reuse detection).
     *
     * <p>{@code noRollbackFor = InvalidRefreshTokenException.class} is critical:
     * when reuse is detected we revoke every active token for the user and
     * <em>then</em> throw. Without this, the default rollback would undo the
     * revocations and leave the attacker's tokens valid.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String plainOldToken, String deviceInfo) {
        String hash = sha256Hex(plainOldToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        OffsetDateTime now = OffsetDateTime.now();

        if (existing.getRevokedAt() != null) {
            // Reuse of a revoked token → assume compromise; revoke every active token for this user.
            revokeAllActiveForUser(existing.getUser(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!existing.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        existing.setRevokedAt(now);
        refreshTokenRepository.save(existing);

        return issue(existing.getUser(), deviceInfo);
    }

    /** Idempotent revoke for logout. Unknown / already-revoked tokens are silently accepted. */
    @Transactional
    public void revoke(String plainToken) {
        String hash = sha256Hex(plainToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private void revokeAllActiveForUser(User user, OffsetDateTime now) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user);
        active.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
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
