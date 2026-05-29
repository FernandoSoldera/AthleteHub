package com.example.athletehub.dto;

import java.util.List;

/**
 * One named meal inside a {@link DietPlanDto} — items materialized so the
 * client renders the day strip in one pass.
 */
public record DietMealDto(
        Long id,
        int position,
        String name,
        String timeHint,
        List<MealItemDto> items
) {}
