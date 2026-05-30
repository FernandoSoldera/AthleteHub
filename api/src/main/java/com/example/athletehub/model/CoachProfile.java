package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Coach-specific public card data. {@code userId} is the PK so it's a
 * strict 1:1 with users — the row is created lazily by the AH-075
 * coach-profile editor (`PUT /api/me/coach-profile`). Rating columns
 * ship pre-zeroed so the card stays stable when a rating feature
 * eventually lands.
 */
@Entity
@Table(name = "coach_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String headline;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "athlete_count", nullable = false)
    private int athleteCount;

    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

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
