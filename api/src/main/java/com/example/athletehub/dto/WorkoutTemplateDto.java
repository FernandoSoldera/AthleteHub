package com.example.athletehub.dto;

import java.util.List;

/**
 * A reusable workout plan with its ordered exercise list materialized.
 * Used as the {@code template} field of {@link TodayPlanResponse} so the
 * Train hero card can render the full prescription without a follow-up
 * request.
 */
public record WorkoutTemplateDto(
        Long id,
        String name,
        String description,
        List<TemplateExerciseDto> exercises
) {}
