package com.example.athletehub.service;

import com.example.athletehub.exception.InvalidResetCodeException;
import com.example.athletehub.model.PasswordResetToken;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.PasswordResetTokenRepository;
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
 * Issues and consumes single-use password-reset codes. Codes are short
 * (6 hex chars) so users can comfortably type them from an email, and they
 * expire quickly. Only the SHA-256 hash is stored; the plain code lives just
 * long enough to be emailed.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 3 random bytes → 6-char hex code; ~24 bits, fine with single-use + short expiry + rate-limit. */
    private static final int CODE_BYTES = 3;

    private final PasswordResetTokenRepository repository;

    @Value("${app.password-reset.code-expiration-minutes:15}")
    private int codeExpirationMinutes;

    /** Generate a fresh reset code for {@code user}, persist its hash, and return the plain code. */
    @Transactional
    public String issueCodeForUser(User user) {
        String plainCode = generateCode();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256Hex(plainCode))
                .expiresAt(OffsetDateTime.now().plusMinutes(codeExpirationMinutes))
                .build();
        repository.save(token);
        return plainCode;
    }

    /**
     * Validate a presented code and mark it consumed. Returns the user it
     * belongs to. Throws {@link InvalidResetCodeException} for any failure
     * (unknown, expired, already used) — they all collapse into a single error
     * so brute-forcing learns nothing about which case applied.
     */
    @Transactional
    public User consumeCode(String plainCode) {
        if (plainCode == null) throw new InvalidResetCodeException();
        String hash = sha256Hex(plainCode);
        PasswordResetToken token = repository.findByTokenHash(hash)
                .orElseThrow(InvalidResetCodeException::new);

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidResetCodeException();
        }

        token.setUsedAt(now);
        repository.save(token);
        return token.getUser();
    }

    public static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
