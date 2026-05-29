package com.example.athletehub.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One measurement on an {@link Evaluation} — either a circumference (cm)
 * or a skinfold (mm) at a named point. {@code pointId} is a free-form
 * stable key like {@code "neck"}, {@code "tricep"}, {@code "suprailiac"};
 * the UNIQUE(evaluation_id, point_id) rule in the schema means
 * re-measuring is an UPDATE, not an append.
 */
@Entity
@Table(name = "evaluation_measurements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "point_id", nullable = false)
    private String pointId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private BigDecimal value;
}
