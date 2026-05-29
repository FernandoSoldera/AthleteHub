package com.example.athletehub.dto;

import com.example.athletehub.model.User;

/**
 * Public view of a User — what's safe to expose in feeds, follow lists,
 * mention rendering, etc. Deliberately omits email, age, height, etc. —
 * those belong on the authenticated /me payload.
 */
public record PublicUserDto(
        Long id,
        String fullName,
        String handle,
        Integer avatarHue,
        String bio
) {
    public static PublicUserDto from(User user) {
        return new PublicUserDto(
                user.getId(),
                user.getFullName(),
                user.getHandle(),
                user.getAvatarHue(),
                user.getBio()
        );
    }
}
