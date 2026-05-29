package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A performed workout — starts {@code in_progress}, becomes {@code completed}
 * or {@code abandoned}. The rollups ({@code totalVolumeKg}, {@code totalSets},
 * {@code prCount}) are recomputed server-side on finish (AH-033); they're
 * stored so the recent-sessions list (AH-035) doesn't have to re-aggregate
 * every time.
 *
 * <p>{@code templateId} survives the template being deleted (Schema FK is
 * {@code ON DELETE SET NULL}) — a session keeps existing on its own.
 */
@Entity
@Table(name = "workout_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "total_volume_kg", nullable = false)
    private BigDecimal totalVolumeKg;

    @Column(name = "total_sets", nullable = false)
    private int totalSets;

    @Column(name = "pr_count", nullable = false)
    private int prCount;

    @Column(nullable = false)
    private String source;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (startedAt == null) startedAt = now;
        if (status == null) status = "in_progress";
        if (totalVolumeKg == null) totalVolumeKg = BigDecimal.ZERO;
        if (source == null) source = "self";
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
