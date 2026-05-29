package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ordered exercise inside a {@link WorkoutSession}. Seeded from the template
 * at session-start, then editable. {@code targetWeight} is numeric here
 * (vs. free-form on the template) so the live workout UI can pre-fill the
 * weight input; we leave it null when the template's {@code target} text
 * isn't a clean number.
 */
@Entity
@Table(name = "session_exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(nullable = false)
    private int position;

    private String scheme;

    @Column(name = "target_weight")
    private BigDecimal targetWeight;
}
