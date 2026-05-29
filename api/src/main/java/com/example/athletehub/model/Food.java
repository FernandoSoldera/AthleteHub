package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A food the user can program into a meal: either a global catalog entry
 * seeded by Flyway ({@code is_global = true}, {@code createdBy = null}) or
 * a custom one belonging to a single user ({@code is_global = false},
 * {@code createdBy = the owner}). The XOR constraint lives in the schema
 * (V20260529150000) — this entity just mirrors the columns.
 *
 * <p>Macros are per {@code serving_size_g} (typically 100 g); the diet
 * calculator scales linearly to whatever amount the user logs.
 */
@Entity
@Table(name = "foods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(name = "is_global", nullable = false)
    private boolean global;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "serving_size_g", nullable = false)
    private BigDecimal servingSizeG;

    @Column(nullable = false)
    private BigDecimal kcal;

    @Column(name = "protein_g", nullable = false)
    private BigDecimal proteinG;

    @Column(name = "carb_g", nullable = false)
    private BigDecimal carbG;

    @Column(name = "fat_g", nullable = false)
    private BigDecimal fatG;

    @Column(name = "fiber_g")
    private BigDecimal fiberG;

    @Column(name = "sodium_mg")
    private BigDecimal sodiumMg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
