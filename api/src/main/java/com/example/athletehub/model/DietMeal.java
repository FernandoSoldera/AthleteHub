package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named meal inside a {@link DietPlan} — "Breakfast",
 * "Post-workout". {@code timeHint} is a free-form HH:MM TEXT; the UI
 * uses it to sort the day's meals on the diet screen but the server
 * doesn't parse it.
 */
@Entity
@Table(name = "diet_meals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DietMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    @Column(name = "time_hint")
    private String timeHint;
}
