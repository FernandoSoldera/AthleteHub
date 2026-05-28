package com.example.athletehub.enums;

/**
 * A user can hold multiple roles and switch between them at runtime
 * (athlete ↔ coach). Stored as TEXT in the {@code user_roles} table; the DB
 * CHECK constraint enforces the same set.
 */
public enum Role {
    ATHLETE,
    COACH
}
