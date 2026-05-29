package com.example.athletehub.dto;

import com.example.athletehub.model.WorkoutSession;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Slim view of a workout session for the recent-sessions list — same
 * rollup fields as {@link WorkoutSessionDto} but no exercises / sets,
 * so a page of 20 rows isn't a fan-out of hydrated children. The client
 * can hit {@code GET /api/workout-sessions/{id}} (lands later) for the
 * full picture on demand.
 */
public record WorkoutSessionSummaryDto(
        Long id,
        Long templateId,
        String title,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Integer durationSeconds,
        BigDecimal totalVolumeKg,
        int totalSets,
        int prCount
) {
    public static WorkoutSessionSummaryDto from(WorkoutSession s) {
        return new WorkoutSessionSummaryDto(
                s.getId(),
                s.getTemplateId(),
                s.getTitle(),
                s.getStatus(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getDurationSeconds(),
                s.getTotalVolumeKg(),
                s.getTotalSets(),
                s.getPrCount()
        );
    }
}
