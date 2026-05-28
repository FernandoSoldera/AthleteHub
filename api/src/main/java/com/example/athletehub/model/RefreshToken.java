package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * A persisted refresh token. Only the {@code tokenHash} (SHA-256 of the random
 * token value) is stored — never the plain token. The plain value is returned
 * to the client at issuance and is the only thing they should send back.
 * Rotation + reuse detection arrive in AH-013.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) issuedAt = OffsetDateTime.now();
    }

    public boolean isActive(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
