package com.example.athletehub.dto;

import java.util.List;

/**
 * A diet plan with its meals → items → foods fully materialized, plus the
 * per-day macro target (sum across all items, scaled).
 */
public record DietPlanDto(
        Long id,
        String name,
        String description,
        boolean library,
        List<DietMealDto> meals,
        Macros dailyTarget
) {}
