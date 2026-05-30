package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The consent edge between a coach user and an athlete user. Status
 * lifecycle: {@code pending → active} on accept (AH-071), {@code active
 * → ended} on either side cancelling. The schema's
 * {@code UNIQUE(coach_id, athlete_id)} means one relationship per pair
 * — a re-invite after an {@code ended} row flips it back to
 * {@code pending} (the service handles that).
 *
 * <p>{@code flag} + {@code adherencePct} stay null until the Epic 9
 * recompute job populates them; {@code lastActivityAt} is touched by
 * events (workout finish, diary entry) elsewhere.
 */
@Entity
@Table(name = "coach_athlete")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachAthlete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coach_id", nullable = false)
    private Long coachId;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column(nullable = false)
    private String status;

    private LocalDate since;

    private String goal;

    private String flag;

    @Column(name = "adherence_pct")
    private Integer adherencePct;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (status == null) status = "pending";
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
