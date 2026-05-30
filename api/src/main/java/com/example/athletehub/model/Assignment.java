package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Coach-prescribed task for one athlete. {@code coachAthleteId} carries
 * the relationship (which also locks down ownership — the coach side of
 * that row is the only one who can edit). {@code refType} + {@code refId}
 * are a soft link to the underlying asset (template / plan / eval
 * request); the schema's XOR CHECK keeps them moving together.
 *
 * <p>{@code status} lifecycle: starts {@code scheduled}; flips to
 * {@code today} when the bucket label needs it (Epic 9 scheduler), to
 * {@code done} / {@code skipped} when completed / abandoned, or
 * {@code pending} for "needs the athlete's attention" coach reminders.
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coach_athlete_id", nullable = false)
    private Long coachAthleteId;

    @Column(nullable = false)
    private String type;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(nullable = false)
    private String status;

    private String notes;

    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (status == null) status = "scheduled";
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
