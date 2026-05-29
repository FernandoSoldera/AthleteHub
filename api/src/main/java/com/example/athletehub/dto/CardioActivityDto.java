package com.example.athletehub.dto;

import com.example.athletehub.model.CardioActivity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Cardio activity view. Same shape as the entity minus the cross-cutting
 * audit fields ({@code created_at}, {@code updated_at}) which clients don't
 * need. {@code source} stays on the wire because the client wants to
 * label imports / coach-assigned cardio differently from self-logged.
 */
public record CardioActivityDto(
        Long id,
        String type,
        BigDecimal distanceM,
        int durationSeconds,
        BigDecimal avgPaceSPerKm,
        BigDecimal avgPowerW,
        Integer avgHr,
        Integer maxHr,
        BigDecimal elevationGainM,
        Integer kcal,
        String notes,
        OffsetDateTime startedAt,
        String source
) {
    public static CardioActivityDto from(CardioActivity c) {
        return new CardioActivityDto(
                c.getId(),
                c.getType(),
                c.getDistanceM(),
                c.getDurationSeconds(),
                c.getAvgPaceSPerKm(),
                c.getAvgPowerW(),
                c.getAvgHr(),
                c.getMaxHr(),
                c.getElevationGainM(),
                c.getKcal(),
                c.getNotes(),
                c.getStartedAt(),
                c.getSource()
        );
    }
}
