package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One food entry inside a {@link DietMeal} — target amount + unit +
 * position. Unit values are constrained by the schema CHECK to
 * {@code g | ml | portion}.
 */
@Entity
@Table(name = "meal_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meal_id", nullable = false)
    private Long mealId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private int position;
}
