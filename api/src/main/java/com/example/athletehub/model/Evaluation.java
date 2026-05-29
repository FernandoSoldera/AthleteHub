package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One body-evaluation row — a weight check-in at minimum, optionally with
 * a body-fat percentage and the method that produced it. The schema's XOR
 * rule keeps {@code bodyFatPct} and {@code bfMethod} agreed on presence:
 * either both are set (a real measurement) or both are null (weight-only
 * check-in).
 *
 * <p>The actual circumference / skinfold values live on
 * {@link EvaluationMeasurement} — kept separate so adding a measurement
 * point doesn't migrate this table.
 */
@Entity
@Table(name = "evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "body_fat_pct")
    private BigDecimal bodyFatPct;

    @Column(name = "bf_method")
    private String bfMethod;

    private String notes;

    @Column(nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (evaluatedAt == null) evaluatedAt = now;
        if (source == null) source = "self";
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
