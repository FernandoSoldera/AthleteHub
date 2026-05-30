package com.example.athletehub.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Coach's "student detail" deep-dive view of one athlete. Composes the
 * relationship row with the athlete's recent training rollup, weekly
 * cardio summary, and latest evaluation. AH-074 will hang assignment
 * status here too.
 *
 * <p>{@code latestEvaluation} is null when the athlete has never logged
 * one; {@code weeklyCardio} is never null (zeros when no cardio in the
 * window); {@code recentSessions} is empty when the athlete hasn't
 * trained yet.
 */
public record StudentDetailDto(
        Long relationshipId,
        String status,
        LocalDate since,
        String goal,
        String flag,
        Integer adherencePct,
        OffsetDateTime lastActivityAt,
        PublicUserDto athlete,
        EvaluationSummaryDto latestEvaluation,
        WeeklySummaryDto weeklyCardio,
        List<WorkoutSessionSummaryDto> recentSessions
) {}
