package com.example.athletehub.dto;

import com.example.athletehub.model.Assignment;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One assignment row as seen by the client. {@code relationshipId}
 * surfaces the {@code coach_athlete} foreign key so the client can map
 * back to the coach when listing across multiple relationships.
 */
public record AssignmentDto(
        Long id,
        Long relationshipId,
        String type,
        String refType,
        Long refId,
        LocalDate scheduledFor,
        String status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AssignmentDto from(Assignment a) {
        return new AssignmentDto(
                a.getId(),
                a.getCoachAthleteId(),
                a.getType(),
                a.getRefType(),
                a.getRefId(),
                a.getScheduledFor(),
                a.getStatus(),
                a.getNotes(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
