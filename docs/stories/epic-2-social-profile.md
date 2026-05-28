# EPIC 2 — Social graph & profile

Following, finding people, and the public profile. Powers the Feed (EPIC 6) and
the "Me" tab.

---

## AH-020 — Schema: follows + counters
**Acceptance criteria**
- [ ] `follows` (follower_id, followee_id, created_at), PK(follower_id, followee_id), index(followee_id).
- [ ] `user_counters` (user_id PK, followers, following, sessions, posts).

## AH-021 — Follow / unfollow
**Acceptance criteria**
- [ ] `POST /api/v1/users/{id}/follow`, `DELETE /api/v1/users/{id}/follow`.
- [ ] `GET /api/v1/me/followers`, `GET /api/v1/me/following` (cursor).
- [ ] Counters updated on follow/unfollow (direct service call in the same tx — no events for MVP).

## AH-022 — Find people + suggestions
**Acceptance criteria**
- [ ] `GET /api/v1/users/search?q=` by name/@handle.
- [ ] `GET /api/v1/users/suggestions` (simple: people you don't follow, with mutual count).

## AH-023 — Public profile aggregate
**Acceptance criteria**
- [ ] `GET /api/v1/users/{handle}` returns header (name, handle, bio, avatar_hue), counters, evolution highlights, recent posts summary, and whether the viewer follows them.

## AH-024 — Client: Find People + Profile
**Acceptance criteria**
- [ ] `services/api/social_api_service.dart` + models.
- [ ] `screens/find_people_screen.dart` — search + suggested list + follow/unfollow buttons.
- [ ] `screens/profile_screen.dart` ("Me") — header, stats, evolution carousel, recent posts grid, account settings list, switch-to-coach action.
- [ ] Plain `setState`; loading/error/empty.

**Technical notes** — Match design `screens-auth-social.jsx` (Find) + `screens-diet-profile.jsx` (Profile).
