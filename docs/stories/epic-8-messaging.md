# EPIC 8 — Messaging

Coach ↔ athlete 1:1 chat (the coach Inbox). **Polling** for MVP (no WebSocket);
push notifies of new messages.

---

## AH-080 — Schema: conversations + messages
**Acceptance criteria**
- [x] `conversations` (id, coach_athlete_id null, last_message_at,
      last_message_preview) — preview capped at 280 chars; partial-
      unique on coach_athlete_id (one thread per relationship).
- [x] `conversation_participants` (conversation_id, user_id,
      last_read_message_id) PK(conversation_id, user_id) — no FK on
      last_read_message_id so a stale pointer is harmless (treated
      as zero unread).
- [x] `messages` (id, conversation_id, sender_id, body 1..4000,
      attachment_media_id null, created_at), index(conversation_id,
      created_at desc). `attachment_media_id` ships as a soft int8
      with no FK so AH-092 can bolt media_assets on without callers
      changing.

## AH-081 — Conversations + messages API
**Acceptance criteria**
- [x] `GET /api/conversations?cursor=&limit=` — hydrated peer +
      unread count, ordered by lastMessageAt DESC NULLS LAST,
      timestamp cursor.
- [x] `GET /api/conversations/{id}/messages?cursor=&limit=` —
      newest-first, id-cursor.
- [x] `POST /api/conversations/{id}/messages` — bumps
      `last_message_at`/`last_message_preview`; auto-advances the
      sender's read pointer. Push to the recipient lands with
      AH-091.
- [x] `POST /api/conversations/{id}/read` advances the read pointer
      to the latest message id.
- [x] Bonus: `POST /api/me/coach-athletes/{id}/conversation` lazily
      creates the thread on first open so clients don't have to
      track conversation ids before sending the first message.
- [x] Visibility chokepoint via `MessagingService.loadVisible` —
      same 404 for "doesn't exist" and "not a participant".

## AH-082 — Client: Inbox + chat
**Acceptance criteria**
- [x] `services/api/messaging_api_service.dart` + 2 models
      (`Conversation`, `Message`).
- [x] `screens/inbox_screen.dart` (threads with unread badges) +
      `screens/chat_screen.dart` (reverse-list message stream +
      composer).
- [x] Polls every 8 s (inbox) / 4 s (chat) while open and on app
      resume; plain `setState`.
- [x] Athlete-side "Message my coach" wired from the profile
      screen's COACHING section; coach-side wired from
      StudentDetail's AppBar action.

**Technical notes** — Match design `TeacherInboxScreen` in `app.jsx`. WebSocket/STOMP is a post-MVP upgrade.
