# EPIC 1 — Identity & Auth

Auth underpins everything. Mirror lotuga's `security/` + `AuthService` shape:
JWT access token + refresh token, email/password, social login, password reset.
Follow [CONVENTIONS.md](../architecture/CONVENTIONS.md) §3.

---

## AH-010 — Schema: users, roles, refresh tokens
**Acceptance criteria**
- [ ] Flyway migration creates `users` (id, email unique, password_hash nullable, full_name, handle unique, avatar_hue, bio, age, height_cm, status, date_joined, timestamps).
- [ ] `user_roles` (user_id, role CHECK in ATHLETE/COACH), PK(user_id, role).
- [ ] `refresh_tokens` (id, user_id, token_hash, device_info, issued_at, expires_at, revoked_at).
- [ ] Migration runs clean on Testcontainers PostgreSQL.

**Technical notes** — Reference lotuga `model/User.java`, `model/RefreshToken.java`, `V*__add_refresh_tokens_table.sql`. Naming `V<yyyyMMddHHmmss>__create_users.sql`.

## AH-011 — Register + password hashing + /me
**Acceptance criteria**
- [ ] `User` entity, `UserRepository`, `AuthService.register(...)`.
- [ ] `POST /api/v1/auth/register` (email, password, full_name, handle) → creates user with `ATHLETE` role, hashed password (BCrypt), returns `ApiResponse`.
- [ ] Duplicate email/handle → 409 with clear error.
- [ ] `GET /api/v1/me` returns the authenticated profile.
- [ ] Unit + IT tests (register happy path + duplicate).

**Technical notes** — `PasswordEncoder` bean (BCrypt). DTOs `SignupRequest`, `UserDto`. Mirror lotuga `AuthService`, `UserController`.

## AH-012 — Login + JWT issuance + security wiring
**Acceptance criteria**
- [ ] `security/JwtUtil` (sign/verify access token, configurable TTL, secret from env).
- [ ] `security/JwtAuthenticationFilter` populating `SecurityContext` from `Authorization: Bearer`.
- [ ] `security/SecurityConfig` stateless; permit auth + actuator; authenticate the rest.
- [ ] `POST /api/v1/auth/login` → `{ accessToken, refreshToken, user }`.
- [ ] `CustomUserDetailsService`, `UserPrincipal` (mirror lotuga).
- [ ] IT: login returns token; protected endpoint rejects without token, accepts with.

**Technical notes** — JJWT 0.12.x. Reference lotuga `security/*`.

## AH-013 — Refresh-token rotation + logout
**Acceptance criteria**
- [ ] `RefreshTokenService` issues, validates, **rotates** (old revoked on use), detects reuse.
- [ ] `POST /api/v1/auth/token/refresh` → new access (+ rotated refresh).
- [ ] `POST /api/v1/auth/logout` revokes the refresh token.
- [ ] IT: refresh works once; reused/expired token rejected.

**Technical notes** — Mirror lotuga `RefreshTokenService`, `RefreshTokenRepository`.

## AH-014 — Password reset via email
**Acceptance criteria**
- [ ] `POST /api/v1/auth/password/forgot` issues a one-time, short-expiry code, emails it (Spring Mail). No account enumeration.
- [ ] `POST /api/v1/auth/password/reset` (code + new password) updates the hash, single-use.
- [ ] IT with **GreenMail** reads the code from the sent email and completes reset.

**Technical notes** — Mirror lotuga `EmailService` + activation/reset flow + GreenMail IT.

## AH-015 — Social login (Apple, Google)
**Acceptance criteria**
- [ ] OAuth2 success/failure handlers; verify provider token; link/create `oauth_accounts` row; issue our JWT pair.
- [ ] `POST /api/v1/auth/oauth/{google|apple}` exchanges a provider token for our tokens.
- [ ] IT covers new-user link and existing-user link.

**Technical notes** — Mirror lotuga `OAuth2AuthenticationSuccessHandler/FailureHandler`, `SocialAuthService`. Add `oauth_accounts` migration.

## AH-016 — Role switch + profile update
**Acceptance criteria**
- [ ] `POST /api/v1/me/roles/switch` (athlete↔coach) — only if the user holds the role; grant COACH on first coach action or via explicit upgrade.
- [ ] `PATCH /api/v1/me` updates bio, full_name, height, avatar_hue.
- [ ] Tests for role gating.

## AH-017 — Client: auth screens + token plumbing
**Acceptance criteria**
- [ ] `services/secure_storage_service.dart` stores/reads tokens (`flutter_secure_storage`).
- [ ] `services/api/http_interceptor.dart` attaches the access token and refreshes on 401 (calls refresh endpoint, retries once, else routes to login).
- [ ] `services/api/auth_api_service.dart` (register, login, refresh, logout, forgot/reset).
- [ ] `models/responses/` auth + token response models (manual `fromJson`).
- [ ] `screens/login_screen.dart`, `signup_screen.dart`, `forgot_password_screen.dart`, `reset_password_screen.dart` matching the design's sign-in screen (segmented sign in / create account, email + password, Apple/Google buttons).
- [ ] Successful auth lands on the main tab shell; tokens persist across restart.

**Technical notes** — Mirror lotuga `lib/services/secure_storage_service.dart`, `lib/services/api/http_interceptor.dart`, `auth_api_service.dart`, and the login/signup screens. Plain `setState`.
