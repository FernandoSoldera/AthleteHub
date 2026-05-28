package com.example.athletehub.enums;

/**
 * Stable, client-facing message codes. The mobile app maps these to localized
 * strings, so the API returns a code (not a hard-coded English sentence).
 * Add codes as features need them.
 */
public enum MessageCode {
    SUCCESS,
    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    INVALID_CREDENTIALS,
    ACCESS_DENIED,
    UNKNOWN_ERROR
}
