package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ordered exercise inside a {@link WorkoutTemplate}. {@code scheme} is a
 * free-form label like "4 × 6-8"; {@code target} is the prescription
 * ("80 kg"). Both are TEXT so coaches can write what they actually say
 * out loud — we don't try to parse them server-side.
 */
@Entity
@Table(name = "workout_template_exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutTemplateExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(nullable = false)
    private int position;

    private String scheme;

    private String target;
}
