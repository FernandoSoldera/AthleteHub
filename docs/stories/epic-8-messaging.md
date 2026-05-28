# EPIC 8 — Messaging

Coach ↔ athlete 1:1 chat (the coach Inbox). **Polling** for MVP (no WebSocket);
push notifies of new messages.

---

## AH-080 — Schema: conversations + messages
**Acceptance criteria**
- [ ] `conversations` (id, coach_athlete_id null, last_message_at, last_message_preview).
- [ ] `conversation_participants` (conversation_id, user_id, last_read_message_id) PK(conversation_id, user_id).
- [ ] `messages` (id, conversation_id, sender_id, body, attachment_media_id null, created_at), index(conversation_id, created_at desc).

## AH-081 — Conversations + messages API
**Acceptance criteria**
- [ ] `GET /api/v1/conversations?cursor=` (with unread count).
- [ ] `GET /api/v1/conversations/{id}/messages?cursor=`.
- [ ] `POST /api/v1/conversations/{id}/messages` (updates `last_message_*`; triggers a push to the recipient — EPIC 9).
- [ ] `POST /api/v1/conversations/{id}/read` advances the read pointer.

## AH-082 — Client: Inbox + chat
**Acceptance criteria**
- [ ] `services/api/messaging_api_service.dart` + models.
- [ ] `screens/inbox_screen.dart` (threads with unread dots) + `screens/chat_screen.dart` (message list + composer).
- [ ] Polls for new messages while open (e.g. every few seconds) and on push; plain `setState`.

**Technical notes** — Match design `TeacherInboxScreen` in `app.jsx`. WebSocket/STOMP is a post-MVP upgrade.
