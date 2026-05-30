-- AH-060: Feed foundation — posts (auto-created from workouts / cardio /
-- evaluations + manual posts), post_likes, post_comments. Columns per
-- docs/architecture/02-data-model.md §4.3 with the MVP simplifications:
--   * no client_uuid (online-first; no offline reconciliation yet)
--   * no image_media_id FK yet — media_assets table arrives in Epic 9.
--     The column exists so AH-061 can land posts that reference a media
--     row when Epic 9 ships, but the FK constraint comes with that
--     migration.
--   * no feed_entries materialized timeline — the architecture spec calls
--     this a Phase 2 fan-out-on-write optimization; MVP uses fan-out-on-
--     read (AH-062 will scan posts directly with the
--     idx_posts_feed_created_active index below).
--
-- Soft-delete via deleted_at on posts + comments so threads stay
-- consistent (a deleted post's comment count stays correct historically);
-- hard delete only on GDPR erase via the user-CASCADE chain.

-- ── posts ─────────────────────────────────────────────────────────────────
-- One row per published item. `type` discriminates how the card renders
-- (workout vs run vs evolution). `source_ref_*` is a soft link back to
-- the row that triggered the auto-post — no FK because we don't want a
-- workout-session delete to be blocked by a post; the soft link goes
-- stale gracefully.
--
-- The XOR check below means source_ref_type and source_ref_id are both
-- set or both null — a "post type without ref" is meaningless.
--
-- like_count + comment_count are denormalized counters maintained by the
-- service so a feed card render is O(1) per post. Rollups land with
-- AH-063.
--
-- `payload` is a JSONB snapshot of what the card rendered at publish
-- time (stats, sparkline values, before/after labels). Snapshotting means
-- a coach renaming an exercise tomorrow doesn't rewrite yesterday's
-- feed cards.
CREATE TABLE posts (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            TEXT         NOT NULL
                                 CHECK (type IN ('workout','run','cycle','evolution','manual')),
    title           TEXT,
    note            TEXT,
    source_ref_type TEXT         CHECK (source_ref_type IS NULL
                                        OR source_ref_type IN ('workout_session','cardio_activity','evaluation')),
    source_ref_id   BIGINT,
    payload         JSONB,
    image_media_id  BIGINT,                       -- FK arrives with Epic 9
    visibility      TEXT         NOT NULL DEFAULT 'followers'
                                 CHECK (visibility IN ('public','followers','private')),
    like_count      INTEGER      NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count   INTEGER      NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- source_ref_type and source_ref_id are both set or both null — a
    -- post can't reference half a source. Manual posts have neither.
    CHECK ( (source_ref_type IS NULL AND source_ref_id IS NULL)
         OR (source_ref_type IS NOT NULL AND source_ref_id IS NOT NULL) )
);

-- Profile timeline reads scan by author, newest first.
CREATE INDEX idx_posts_author_created ON posts (author_id, created_at DESC);

-- Home-feed read is the hottest query — partial index on the active rows
-- only so soft-deleted posts don't bloat the scan.
CREATE INDEX idx_posts_feed_created_active ON posts (created_at DESC)
    WHERE deleted_at IS NULL;

-- ── post_likes ────────────────────────────────────────────────────────────
-- Composite PK enforces "one like per (post, user)" — a second tap is a
-- no-op at the data layer. CASCADE on both FKs so a post or user
-- deletion sweeps likes without a separate cleanup step.
CREATE TABLE post_likes (
    post_id     BIGINT       NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

-- "Who liked recently?" / "what did I like?" both scan user-side.
CREATE INDEX idx_post_likes_user ON post_likes (user_id, created_at DESC);

-- ── post_comments ─────────────────────────────────────────────────────────
-- Soft-delete via deleted_at — collapse a comment without orphaning the
-- thread. The body stays in place so a moderation audit can still read it;
-- hard delete only on GDPR.
CREATE TABLE post_comments (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     BIGINT       NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        TEXT         NOT NULL CHECK (LENGTH(body) > 0),
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Comments are loaded per post, oldest first (chronological thread).
CREATE INDEX idx_post_comments_post_created ON post_comments (post_id, created_at);
