package com.example.athletehub.dto;

import com.example.athletehub.model.CoachProfile;

import java.math.BigDecimal;

/**
 * Public view of a coach card. Returned by {@code GET /api/me/coach-profile}
 * (caller is the coach) and — when AH-075 surfaces it — by
 * {@code GET /api/users/{handle}} for any visitor.
 */
public record CoachProfileDto(
        Long userId,
        String headline,
        Integer yearsExperience,
        int athleteCount,
        BigDecimal ratingAvg,
        int ratingCount
) {
    public static CoachProfileDto from(CoachProfile p) {
        return new CoachProfileDto(
                p.getUserId(),
                p.getHeadline(),
                p.getYearsExperience(),
                p.getAthleteCount(),
                p.getRatingAvg(),
                p.getRatingCount());
    }
}
