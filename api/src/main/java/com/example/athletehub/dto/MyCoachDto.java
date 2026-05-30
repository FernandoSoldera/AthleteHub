package com.example.athletehub.dto;

import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.User;

import java.time.LocalDate;

/**
 * Athlete-side counterpart to {@link RosterEntryDto} — the active coach
 * relationship surfaced with the coach hydrated. The {@code GET
 * /api/me/coach} endpoint returns a single object (the MVP assumes 1:1
 * coach↔athlete; per
 * {@code 02-data-model.md §4.8} the conversations table comments
 * "1:1 coach<->athlete at MVP") or null when the athlete has no active
 * coach.
 */
public record MyCoachDto(
        Long id,
        String status,
        LocalDate since,
        String goal,
        PublicUserDto coach
) {
    public static MyCoachDto from(CoachAthlete row, User coach) {
        return new MyCoachDto(
                row.getId(),
                row.getStatus(),
                row.getSince(),
                row.getGoal(),
                coach == null ? null : PublicUserDto.from(coach));
    }
}
