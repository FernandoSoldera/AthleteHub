# EPIC 9 — Notifications & Media

Push notifications (Firebase), in-app notifications, scheduled eval reminders, and
progress-photo uploads.

---

## AH-090 — Devices + push integration
**Acceptance criteria**
- [ ] `devices` schema (id, user_id, platform CHECK(ios|android), push_token unique, app_version, last_seen_at).
- [ ] `POST /api/v1/devices` registers/updates a token; `DELETE /api/v1/devices/{id}`.
- [ ] Firebase Admin SDK wired (`config/FirebaseConfig`); a `NotificationService.sendPush(userId, ...)` sends to the user's devices.

**Technical notes** — Mirror lotuga `config/FirebaseConfig` + push send path.

## AH-091 — In-app notifications + scheduled reminders
**Acceptance criteria**
- [ ] `notifications` + `scheduled_notifications` schema.
- [ ] `GET /api/v1/notifications?cursor=`, `POST /api/v1/notifications/read`.
- [ ] An `@Scheduled` dispatcher sends due `scheduled_notifications` (eval reminder 24h before the eval_request) via push + in-app.
- [ ] Notifications also created for: PR achieved, new assignment, new message, social like/comment/follow.

**Technical notes** — `scheduler/` package; guard for single-instance execution (MVP: single instance, so a simple `@Scheduled` is fine).

## AH-092 — Media upload (progress photos)
**Acceptance criteria**
- [ ] `media_assets` schema (id, owner_id, kind, storage_key, content_type, status, dims).
- [ ] `POST /api/v1/media/upload-url` returns a signed upload URL + media id; `POST /api/v1/media/{id}/complete` marks ready.
- [ ] MVP storage: S3-compatible bucket (or local/dev MinIO); serve via signed URLs. Strip EXIF on the client before upload.

**Technical notes** — Keep it minimal; if S3 is too much for MVP, a DB-backed blob or local volume behind the same API contract is acceptable, swap later.

## AH-093 — Client: push, notif inbox, image upload
**Acceptance criteria**
- [ ] `firebase_messaging` setup; register token via `POST /devices` after login; handle foreground (in-app banner) + background (deep link).
- [ ] Notification inbox screen.
- [ ] Image picker + compression + resumable upload for progress photos (used in evaluations + evolution posts).

**Technical notes** — Mirror lotuga `lib/services/notification_service.dart`.
