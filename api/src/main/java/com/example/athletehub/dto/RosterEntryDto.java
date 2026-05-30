package com.example.athletehub.dto;

import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.User;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row on a coach's roster — the relationship stats + the athlete
 * hydrated. The {@code flag} + {@code adherencePct} fields are surfaced
 * verbatim from {@code coach_athlete} and stay null until Epic 9's
 * recompute job populates them; the client renders "new" / "—"
 * placeholders for null values.
 *
 * <p>Distinct from {@link CoachInviteDto} because the roster view doesn't
 * carry the coach side (the viewer <em>is</em> the coach).
 */
public record RosterEntryDto(
        Long id,
        String status,
        LocalDate since,
        String goal,
        String flag,
        Integer adherencePct,
        OffsetDateTime lastActivityAt,
        PublicUserDto athlete
) {
    public static RosterEntryDto from(CoachAthlete row, User athlete) {
        return new RosterEntryDto(
                row.getId(),
                row.getStatus(),
                row.getSince(),
                row.getGoal(),
                row.getFlag(),
                row.getAdherencePct(),
                row.getLastActivityAt(),
                athlete == null ? null : PublicUserDto.from(athlete));
    }
}
