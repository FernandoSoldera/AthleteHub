package com.example.athletehub.dto;

import com.example.athletehub.model.Food;

import java.math.BigDecimal;

/**
 * Public view of a food. {@code custom} flips true for the caller's own
 * additions so the client can label them ("Custom" badge) without
 * re-deriving from {@code createdBy}. We don't expose the owner id
 * because customs are private to their creator — if you can see one, you
 * own it.
 */
public record FoodDto(
        Long id,
        String name,
        String brand,
        boolean custom,
        BigDecimal servingSizeG,
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        BigDecimal fiberG,
        BigDecimal sodiumMg
) {
    public static FoodDto from(Food f) {
        return new FoodDto(
                f.getId(),
                f.getName(),
                f.getBrand(),
                !f.isGlobal(),
                f.getServingSizeG(),
                f.getKcal(),
                f.getProteinG(),
                f.getCarbG(),
                f.getFatG(),
                f.getFiberG(),
                f.getSodiumMg()
        );
    }
}
