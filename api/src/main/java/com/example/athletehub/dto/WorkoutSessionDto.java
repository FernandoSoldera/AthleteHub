package com.example.athletehub.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A workout session view — covers both the response of "session started"
 * (AH-032) and "session in progress / completed" (AH-033 / AH-035). The
 * rollup fields ({@code totalVolumeKg}, {@code totalSets}, {@code prCount})
 * stay at zero until the finish endpoint computes them.
 */
public record WorkoutSessionDto(
        Long id,
        Long templateId,
        String title,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Integer durationSeconds,
        BigDecimal totalVolumeKg,
        int totalSets,
        int prCount,
        List<SessionExerciseDto> exercises
) {}
