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
    EXERCISE_ALREADY_EXISTS,

    // Training (EPIC 3 — AH-032)
    ACTIVE_SESSION_EXISTS,
    TEMPLATE_NOT_FOUND,

    // Training (EPIC 3 — AH-033)
    SESSION_NOT_FOUND,
    SESSION_NOT_IN_PROGRESS,
    INVALID_SET_OP,

    // Body / Evolution (EPIC 4 — AH-041)
    EVALUATION_NOT_FOUND,
    BF_METHOD_NOT_SUPPORTED,
    BF_MANUAL_REQUIRES_PCT,
    BF_MISSING_MEASUREMENTS,
    BF_MISSING_USER_FIELD,

    // Body / Evolution (EPIC 4 — AH-042)
    INVALID_METRIC,
    INVALID_RANGE,

    // Nutrition (EPIC 5 — AH-051)
    FOOD_ALREADY_EXISTS
}
