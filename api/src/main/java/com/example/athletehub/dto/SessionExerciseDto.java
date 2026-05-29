package com.example.athletehub.dto;

import java.math.BigDecimal;

/**
 * Exercise slot inside a live or completed workout. Carries the catalog
 * name so the client can render the live workout screen without hitting
 * the exercise catalog endpoint per row.
 */
public record SessionExerciseDto(
        Long id,
        Long exerciseId,
        String name,
        int position,
        String scheme,
        BigDecimal targetWeight
) {}
