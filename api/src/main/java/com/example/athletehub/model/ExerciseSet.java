package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A single set in a live or completed workout. The {@code is_pr} flag is
 * set by the finish-session pass (AH-033) when this set's
 * (weight, reps) produced a new personal-record for its exercise on at
 * least one metric (e1RM or max_weight); kept on the set row so the live
 * workout UI can highlight specific lines without joining back through
 * {@code personal_records}.
 */
@Entity
@Table(name = "exercise_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_exercise_id", nullable = false)
    private Long sessionExerciseId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    private Integer reps;

    private BigDecimal rpe;

    @Column(name = "is_done", nullable = false)
    private boolean done;

    @Column(name = "is_pr", nullable = false)
    private boolean pr;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
