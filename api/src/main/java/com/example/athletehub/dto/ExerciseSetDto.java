package com.example.athletehub.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One set as seen by the client. {@code pr} is set by the finish-session
 * pass; while a session is in progress every set's {@code pr} is false.
 */
public record ExerciseSetDto(
        Long id,
        Long sessionExerciseId,
        int setNumber,
        BigDecimal weightKg,
        Integer reps,
        BigDecimal rpe,
        boolean done,
        boolean pr,
        OffsetDateTime completedAt
) {}
