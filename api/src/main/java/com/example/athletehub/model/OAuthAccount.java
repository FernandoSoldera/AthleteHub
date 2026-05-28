package com.example.athletehub.model;

import com.example.athletehub.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Links a {@link User} to an external identity provider account. A user can
 * have multiple rows here (one per provider) and may also have a local
 * password — they aren't mutually exclusive.
 */
@Entity
@Table(name = "oauth_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_uid"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_uid", nullable = false)
    private String providerUid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
