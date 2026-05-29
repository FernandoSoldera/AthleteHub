-- AH-020: Social graph foundation.
--
--   follows         — directed edge (follower_id, followee_id) with a
--                     surrogate id so cursor pagination is a trivial
--                     id-DESC scan, plus indexes for both directions.
--   user_counters   — denormalized per-user totals (followers, following,
--                     sessions, posts) so a profile read is one row, not
--                     two COUNT(*) queries. Kept in sync inside the same
--                     transaction as the underlying event (follow/unfollow,
--                     workout finish, post publish).

CREATE TABLE follows (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    follower_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);

-- Cursor pagination on followers / following: order by id DESC, page on id < cursor.
CREATE INDEX idx_follows_followee ON follows (followee_id, id DESC);
CREATE INDEX idx_follows_follower ON follows (follower_id, id DESC);

CREATE TABLE user_counters (
    user_id    BIGINT  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    followers  INTEGER NOT NULL DEFAULT 0,
    following  INTEGER NOT NULL DEFAULT 0,
    sessions   INTEGER NOT NULL DEFAULT 0,
    posts      INTEGER NOT NULL DEFAULT 0
);

-- Backfill rows for any pre-existing users so updates can be plain UPDATEs
-- (no UPSERT) from here on.
INSERT INTO user_counters (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;
