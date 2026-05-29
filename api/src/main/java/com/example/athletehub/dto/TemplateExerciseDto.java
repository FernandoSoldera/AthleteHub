package com.example.athletehub.dto;

/**
 * One slot in a workout template: position + the exercise being prescribed
 * + the coach-written scheme ("4 × 6-8") + target ("80 kg"). The exercise
 * id and name come from {@code workout_template_exercises} joined with
 * {@code exercises}; we expose the name directly so the client doesn't
 * have to issue a second request to hydrate the catalog.
 */
public record TemplateExerciseDto(
        Long exerciseId,
        String name,
        int position,
        String scheme,
        String target
) {}
