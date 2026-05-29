package com.example.athletehub.enums;

/**
 * Stable, client-facing message codes. The mobile app maps these to localized
 * strings (see {@code assets/i18n/en.json} → {@code errors.*} / {@code success.*}),
 * so the API returns a code, not a hard-coded English sentence.
 * Add codes as features need them; never reuse one for a different meaning.
 */
public enum MessageCode {
    // Generic
    SUCCESS,
    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    INVALID_CREDENTIALS,
    ACCESS_DENIED,
    UNKNOWN_ERROR,

    // Identity / signup (EPIC 1)
    EMAIL_ALREADY_REGISTERED,
    HANDLE_ALREADY_TAKEN,

    // Identity / tokens (EPIC 1 — AH-013)
    INVALID_REFRESH_TOKEN,

    // Identity / password reset (EPIC 1 — AH-014)
    INVALID_RESET_CODE,

    // Identity / social login (EPIC 1 — AH-015)
    INVALID_OAUTH_TOKEN,

    // Training (EPIC 3 — AH-031)
    EXERCISE_ALREADY_EXISTS
}
