package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What the user actually ate, when. {@code mealLabel} is free-form so
 * users can bucket entries however they like ("Pre-workout",
 * "Cheat meal"). {@code source} ∈ {self, plan, favorite, coach}; the
 * 'coach' value is forward-compatible (Epic 7).
 */
@Entity
@Table(name = "diary_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "eaten_at", nullable = false)
    private OffsetDateTime eatenAt;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String unit;

    @Column(name = "meal_label")
    private String mealLabel;

    @Column(nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (eatenAt == null) eatenAt = now;
        if (source == null) source = "self";
    }
}
