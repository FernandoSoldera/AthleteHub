# EPIC 6 — Feed

The activity feed of posts from people you follow. Posts are created **directly**
when an athlete finishes a workout/cardio/evaluation (no event bus for MVP).

---

## AH-060 — Schema: posts, likes, comments
**Acceptance criteria**
- [ ] `posts` (id, author_id, type CHECK(workout|run|cycle|evolution), title, note, source_ref_type, source_ref_id, payload jsonb, image_media_id null, visibility, like_count, comment_count, deleted_at, created_at).
- [ ] `post_likes` (post_id, user_id) PK; `post_comments` (id, post_id, author_id, body, deleted_at, created_at).
- [ ] Indexes `posts(author_id, created_at desc)`, `posts(created_at desc) where deleted_at is null`.

## AH-061 — Create posts
**Acceptance criteria**
- [ ] On `finish workout` / `log cardio` / `save evaluation`, the respective service creates a `posts` row with a denormalized `payload` snapshot (stats/chart/before-after).
- [ ] `POST /api/v1/posts` for manual posts; `DELETE /api/v1/posts/{id}` soft-delete.

**Technical notes** — Direct method call from training/body services into a `FeedService.publish(...)`. No outbox/Kafka.

## AH-062 — Feed timeline
**Acceptance criteria**
- [ ] `GET /api/v1/feed?filter=all|workout|run|cycle|evolution&cursor=` — fan-out-on-read: posts from followees (+ self), newest first, cursor paginated.
- [ ] Hydrated cards: author, payload, like/comment counts, and viewer's `liked?`.

## AH-063 — Like / comment / share
**Acceptance criteria**
- [ ] `POST/DELETE /api/v1/posts/{id}/like` (maintains `like_count`).
- [ ] `GET/POST /api/v1/posts/{id}/comments` (maintains `comment_count`).
- [ ] `GET /api/v1/posts/{id}/share` returns a shareable payload/link.

## AH-064 — Client: Feed
**Acceptance criteria**
- [ ] `services/api/feed_api_service.dart` + models.
- [ ] `screens/feed_screen.dart` — filter chips, list of `widgets/feed_card.dart` (avatar, type tag, title, note, stats grid, sparkline chart, like/comment/share).
- [ ] Optimistic like toggle; pull-to-refresh; pagination; loading/error/empty.

**Technical notes** — Match design `screens-auth-social.jsx` (Feed + FeedCard).
