package com.example.athletehub.dto;

import com.example.athletehub.model.Evaluation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Slim view of an evaluation for the recent-evaluations list — no
 * measurements, so a 20-row page stays a single SELECT. The client hits
 * {@code GET /api/evaluations/{id}} for the hydrated payload on demand.
 */
public record EvaluationSummaryDto(
        Long id,
        OffsetDateTime evaluatedAt,
        BigDecimal weightKg,
        BigDecimal bodyFatPct,
        String bfMethod,
        String source
) {
    public static EvaluationSummaryDto from(Evaluation e) {
        return new EvaluationSummaryDto(
                e.getId(),
                e.getEvaluatedAt(),
                e.getWeightKg(),
                e.getBodyFatPct(),
                e.getBfMethod(),
                e.getSource());
    }
}
