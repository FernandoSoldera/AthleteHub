package com.example.athletehub.dto;

import com.example.athletehub.model.Evaluation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full evaluation view — the row plus its measurements, materialized so a
 * single GET reconstructs the entire Evolution-detail screen. The body-fat
 * pair ({@code bodyFatPct}, {@code bfMethod}) stays null for a weight-only
 * check-in; the schema XOR rule keeps them agreed.
 */
public record EvaluationDto(
        Long id,
        OffsetDateTime evaluatedAt,
        BigDecimal weightKg,
        BigDecimal bodyFatPct,
        String bfMethod,
        String notes,
        String source,
        List<EvaluationMeasurementDto> measurements
) {
    public static EvaluationDto from(Evaluation e, List<EvaluationMeasurementDto> measurements) {
        return new EvaluationDto(
                e.getId(),
                e.getEvaluatedAt(),
                e.getWeightKg(),
                e.getBodyFatPct(),
                e.getBfMethod(),
                e.getNotes(),
                e.getSource(),
                measurements);
    }
}
