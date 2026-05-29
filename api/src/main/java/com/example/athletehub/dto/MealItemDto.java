package com.example.athletehub.dto;

import java.math.BigDecimal;

/**
 * One item inside a {@link DietMealDto} — the food + the prescription
 * amount/unit + its scaled macros. The scaled macros are computed
 * server-side so the client can render the meal card without re-doing
 * the math, and so totals/target/remaining come out consistent.
 */
public record MealItemDto(
        Long id,
        int position,
        BigDecimal amount,
        String unit,
        FoodDto food,
        Macros macros
) {}
