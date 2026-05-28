# 05 · Frontend (Flutter) — MVP type-based

> Updated for the MVP. Authoritative details in [CONVENTIONS.md](CONVENTIONS.md)
> §4. We mirror the `lotuga` Flutter app: **type-based folders + plain
> `setState` + the `http` package**, online-first. **No** Riverpod/Bloc, **no**
> Drift/offline DB, **no** Dio. (Offline-first + state-mgmt are post-MVP upgrades.)

## 1. Structure (by type, not feature)

```
client/lib/
├── main.dart            # app shell: theme + bottom tabs (role-aware)
├── firebase_options.dart
├── config/              # app_config.dart (env, base URL)
├── i18n/                # app_localizations.dart (+ assets/i18n/en.json, pt.json)
├── models/              # plain Dart classes w/ manual fromJson/toJson
│   └── responses/       # *_response.dart API envelopes
├── screens/             # one widget per screen
├── services/            # auth, secure storage, notifications
│   └── api/             # *_api_service.dart + http_interceptor.dart
├── styles/              # app_theme.dart (dark/light + accents)
└── widgets/             # reusable widgets incl. fl_chart wrappers
```

## 2. State management — plain `setState`

A `StatefulWidget` per screen calls an api service, holds the result in local
state, and renders loading / error / empty / data. No global state library. This
matches lotuga and is sufficient for the MVP's screens. (If shared/complex state
emerges, introducing a state-mgmt library is a contained later change.)

## 3. Networking

- `http` package wrapped in `services/api/<domain>_api_service.dart`.
- `services/api/http_interceptor.dart` attaches the JWT and refreshes on 401
  (retry once, else route to login).
- Models parse JSON **manually** via `fromJson` (no codegen). Mirror lotuga's
  `models/` + `models/responses/`.
- **REST only.** No GraphQL, no WebSocket (chat/feed poll for MVP).

## 4. Theming & charts

- `styles/app_theme.dart` builds dark + light `ThemeData` and the four accent
  palettes (volt/cyan/magenta/orange) from the design's `tokens.css`.
- Reusable design components in `widgets/` (`AhCard`, `AhChip`, `AhStat`,
  `AhSegmented`, top bar, bottom tabs).
- Charts via **`fl_chart`** wrappers (`widgets/`): feed sparklines, weight/arm/
  waist/bench series, cardio pace, the diet macro ring.

## 5. Platform concerns

- Tokens in `flutter_secure_storage`; config via `flutter_dotenv` (`.env`).
- i18n via JSON assets + `app_localizations.dart` (en + pt), like lotuga.
- Push via `firebase_messaging` (register token after login; deep-link on tap).
- Social sign-in via `google_sign_in` + `sign_in_with_apple`.
- Single codebase → iOS + Android; respect safe areas; client-side rest timer in
  the live workout screen needs no network.

## 6. What's deferred (was in the original plan)

Offline-first local DB + sync outbox + conflict resolution, Riverpod, go_router,
Dio, OpenAPI codegen, GraphQL client, WebSocket realtime. All are sensible
**post-MVP** upgrades; none are needed to ship. Online-first with clear loading
states is the MVP bar.

## 7. Screens → stories

Feed, Find People, Profile (EPIC 2/6); Train, Workout, Cardio (EPIC 3); Evolution,
New Evaluation, Graph (EPIC 4); Diet, Add Food (EPIC 5); Coach dashboard, Student
detail, Assign, Schedule, Library, Coach profile (EPIC 7); Inbox, Chat (EPIC 8);
Auth screens (EPIC 1). See [../stories/BACKLOG.md](../stories/BACKLOG.md).
