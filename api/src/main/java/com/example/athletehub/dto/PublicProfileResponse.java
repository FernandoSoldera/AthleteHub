package com.example.athletehub.dto;

/**
 * Aggregate payload returned by `GET /api/users/{handle}`. The viewer-scoped
 * {@code iFollow} flag lets the client render the follow/unfollow button
 * without a second round-trip.
 */
public record PublicProfileResponse(
        PublicUserDto user,
        Integer followers,
        Integer following,
        Boolean iFollow
) {}
