-- AH-040 follow-up: add `sex` to users so AH-041 body-fat formulas
-- (Jackson-Pollock 7-site, Durnin, Navy) can be computed server-side.
--
-- Nullable on purpose: existing users land with NULL and stay valid; new
-- users can omit it at signup and pick it up later via PATCH /me when
-- they want body-fat auto-computation. The Body / Evolution flow surfaces
-- `manual` as the bf_method for users who'd rather not specify.
--
-- The value set is binary (male / female) because the only thing this
-- column drives is the biological-sex coefficients in the body-fat
-- formulas — Jackson-Pollock and Durnin literally have different
-- equations for each. This is a measurement constraint, not a social
-- one; a future feature that wants social-identity gender would land
-- on a separate column with its own value set.

ALTER TABLE users
    ADD COLUMN sex TEXT
    CHECK (sex IS NULL OR sex IN ('male', 'female'));
