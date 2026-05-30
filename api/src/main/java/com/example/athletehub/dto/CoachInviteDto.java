package com.example.athletehub.dto;

import com.example.athletehub.model.CoachAthlete;
import com.example.athletehub.model.User;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A coach↔athlete relationship row with both sides hydrated. Used by
 * AH-071 for the invite + invite-inbox endpoints; later stories (the
 * coach dashboard in AH-072) will use a richer DTO carrying the
 * adherence / flag fields too.
 */
public record CoachInviteDto(
        Long id,
        String status,
        LocalDate since,
        OffsetDateTime createdAt,
        PublicUserDto coach,
        PublicUserDto athlete
) {
    public static CoachInviteDto from(CoachAthlete row, User coach, User athlete) {
        return new CoachInviteDto(
                row.getId(),
                row.getStatus(),
                row.getSince(),
                row.getCreatedAt(),
                coach == null ? null : PublicUserDto.from(coach),
                athlete == null ? null : PublicUserDto.from(athlete));
    }
}
