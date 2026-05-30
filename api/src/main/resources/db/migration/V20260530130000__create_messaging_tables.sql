-- AH-080: Messaging foundation — conversations (the thread row),
-- conversation_participants (the membership + read pointer), messages
-- (the message stream). Per docs/architecture/02-data-model.md §4.8
-- with the MVP simplifications:
--   * no client_uuid on messages (online-first; offline DB is post-MVP)
--   * attachment_media_id stays a soft int8 with no FK — media_assets
--     lands in AH-092; the FK can be added then without rewriting
--     callers (same soft-link pattern as assignments.ref_type / ref_id)
--   * 1:1 coach<->athlete conversations only — the architecture doc's
--     comment "1:1 coach<->athlete at MVP" applies. coach_athlete_id is
--     the natural-key tag so the conversation can be looked up from a
--     relationship row (and so a unique constraint prevents accidental
--     double-creation per relationship).
--
-- The membership row carries the read pointer, *not* the messages
-- table — keeps "unread count" a single per-user lookup against the
-- thread instead of a scan over message rows.

-- ── conversations ────────────────────────────────────────────────────────
-- One row per thread. coach_athlete_id is nullable so non-coaching
-- conversations (a future "DM between two athletes" feature) don't need
-- a schema change; the unique index is partial so it only enforces 1:1
-- per coach_athlete relationship when the tag is set.
--
-- last_message_at + last_message_preview are denormalised hot fields —
-- updated by the send-message path so the inbox list (the n+1
-- bottleneck) is one indexed read per thread instead of a correlated
-- subquery into messages.
CREATE TABLE conversations (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coach_athlete_id       BIGINT       REFERENCES coach_athlete(id) ON DELETE SET NULL,
    last_message_at        TIMESTAMPTZ,
    last_message_preview   TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (last_message_preview IS NULL OR length(last_message_preview) <= 280)
);

-- One conversation per coach_athlete row (partial — only enforce when
-- the relationship FK is set; non-tagged threads can multiply freely).
CREATE UNIQUE INDEX uq_conversations_coach_athlete
    ON conversations (coach_athlete_id)
    WHERE coach_athlete_id IS NOT NULL;

-- Inbox-ordering covered index: list newest-first.
CREATE INDEX idx_conversations_last_message_at
    ON conversations (last_message_at DESC NULLS LAST);

-- ── conversation_participants ────────────────────────────────────────────
-- Composite-PK membership row. last_read_message_id advances when the
-- viewer opens the thread (POST /conversations/{id}/read in AH-081);
-- "unread" is messages.id > last_read_message_id, single index hit.
--
-- No FK from last_read_message_id → messages — messages get hard-deleted
-- when a participant leaves a thread (rare, but the FK would cascade in
-- the wrong direction). Stale pointers are harmless: an unread query
-- treats "pointer past the latest message" as zero unread.
CREATE TABLE conversation_participants (
    conversation_id        BIGINT       NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id                BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id   BIGINT,
    joined_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

-- "What threads is this user in?" — covers the inbox query's join.
CREATE INDEX idx_conversation_participants_user
    ON conversation_participants (user_id);

-- ── messages ─────────────────────────────────────────────────────────────
-- The message stream. body is required and non-empty (an attachment-only
-- message still gets a body — even if the client sends "📎"; saves
-- inbox-preview rendering from a NULL-check). attachment_media_id ships
-- as a nullable int8 with no FK; AH-092 lands the media_assets table and
-- bolts the FK on then.
CREATE TABLE messages (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id        BIGINT       NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body                   TEXT         NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
    attachment_media_id    BIGINT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (attachment_media_id IS NULL OR attachment_media_id > 0)
);

-- The hot index: thread paging is newest-first per conversation. The
-- backwards order matches the chat-screen scroll direction and is the
-- only sort the messages endpoint ships with.
CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);
