package com.example.athletehub.dto;

/**
 * Public view of a suggested user — same shape as {@link PublicUserDto} but
 * with the mutual-follow count, which is how the Find People screen surfaces
 * "@mayalifts · 12 mutual". Mutuals are users I follow that this person also
 * follows.
 */
public record SuggestedUserDto(
        Long id,
        String fullName,
        String handle,
        Integer avatarHue,
        String bio,
        Long mutualCount
) {}
