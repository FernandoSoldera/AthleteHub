package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The user's current best on a (exercise, metric) pair. Exactly one row
 * per combination (UNIQUE constraint in the schema). PR history is
 * reconstructable from the workout sessions themselves; this table just
 * holds the "current best" so the Train screen / profile doesn't have to
 * scan the whole history.
 */
@Entity
@Table(name = "personal_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "achieved_at", nullable = false)
    private OffsetDateTime achievedAt;

    @Column(name = "session_id")
    private Long sessionId;
}
