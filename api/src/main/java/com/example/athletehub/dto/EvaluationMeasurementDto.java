package com.example.athletehub.dto;

import com.example.athletehub.model.EvaluationMeasurement;

import java.math.BigDecimal;

/**
 * One measurement returned with an {@link EvaluationDto}. Free-form
 * {@code pointId} keeps new points (a new skinfold site, per-thigh
 * circumference) cheap — no migration, no DTO change.
 */
public record EvaluationMeasurementDto(
        String pointId,
        String kind,
        String unit,
        BigDecimal value
) {
    public static EvaluationMeasurementDto from(EvaluationMeasurement m) {
        return new EvaluationMeasurementDto(m.getPointId(), m.getKind(), m.getUnit(), m.getValue());
    }
}
