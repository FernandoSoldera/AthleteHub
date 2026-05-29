package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * An exercise the user can program into a workout: either a global catalog
 * entry seeded by Flyway (is_global = true, createdBy = null) or a custom
 * one belonging to a single user (is_global = false, createdBy = the owner).
 * The XOR constraint lives in the schema (V20260528150000) — this entity
 * just mirrors the columns.
 *
 * <p>{@code createdBy} stores the raw user id rather than a User association
 * so listing pages don't drag the whole user row along for no reason; the
 * service does targeted hydration when it needs the owner.
 */
@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "primary_muscle")
    private String primaryMuscle;

    private String equipment;

    @Column(name = "is_global", nullable = false)
    private boolean global;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
