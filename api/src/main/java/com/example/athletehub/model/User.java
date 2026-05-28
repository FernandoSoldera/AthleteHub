package com.example.athletehub.model;

import com.example.athletehub.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * One user account. The same user can hold multiple roles (athlete + coach) and
 * switch between them in the app. Schema lives in
 * {@code V20260527130000__create_users_roles_refresh_tokens.sql}.
 *
 * <p>{@code passwordHash} is nullable so OAuth-only users (no local password)
 * fit the same model (AH-015).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(name = "avatar_hue")
    private Integer avatarHue;

    private String bio;

    private Integer age;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "date_joined", nullable = false)
    private LocalDate dateJoined;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role.class)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (dateJoined == null) dateJoined = LocalDate.now();
        if (status == null) status = "active";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
