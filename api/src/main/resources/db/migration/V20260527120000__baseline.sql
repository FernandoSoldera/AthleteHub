-- Baseline migration for AthleteHub.
-- Intentionally empty: it establishes the Flyway baseline (version 0+) so the
-- schema is owned entirely by versioned migrations from here on.
-- Real tables start in EPIC 1 (AH-010: users, roles, refresh_tokens).
-- Postgres extensions (e.g. btree_gist) will be enabled in the migration that
-- first needs them, not pre-emptively here.
SELECT 1;
