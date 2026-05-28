# EPIC 4 — Body / Evolution

Body evaluations (13 circumferences + 8 skinfolds), body-fat computation, and the
metric progress graphs.

---

## AH-040 — Schema: evaluations + measurements
**Acceptance criteria**
- [ ] Migration `evaluations` (id, user_id, evaluated_at, assigned_by_coach_id null, weight_kg, body_fat_pct null, bf_method, notes, source).
- [ ] `evaluation_measurements` (id, evaluation_id, point_id, kind CHECK(circumference|skinfold), unit CHECK(cm|mm), value), UNIQUE(evaluation_id, point_id).
- [ ] Index `evaluations(user_id, evaluated_at desc)`.

**Technical notes** — point_ids match the manikin: 13 circ (neck, shoulder, chest, arm_l/r, forearm_l/r, waist, hip, thigh_l/r, calf_l/r) + 8 skinfold (tricep, chest_f, subscap, suprail, abdomen_f, axilla, thigh_f, calf_f).

## AH-041 — Save evaluation + body-fat
**Acceptance criteria**
- [ ] `POST /api/v1/evaluations` saves the evaluation + all measurements in one transaction.
- [ ] Server computes `body_fat_pct` from skinfolds (Jackson-Pollock 7-site default) when enough sites are present; else stores manual value.
- [ ] `GET /api/v1/evaluations?cursor=` and `GET /api/v1/evaluations/{id}`.
- [ ] IT: posting measurements yields the expected body-fat %.

**Technical notes** — Keep the BF formula in a `util/` helper with unit tests.

## AH-042 — Body overview + metric series
**Acceptance criteria**
- [ ] `GET /api/v1/body/overview` — latest weight + deltas (bodyfat/arm/waist) + graph-row series + recent evaluations (one aggregate for the Evolution screen).
- [ ] `GET /api/v1/body/metrics/{metric}?range=4w|12w|6m|1y` for weight/arm/waist/bench (bench pulls from `personal_records`).
- [ ] Series derived by querying evaluations/measurements (no separate table).

## AH-043 — Client: Evolution / New Evaluation / Graph
**Acceptance criteria**
- [ ] `services/api/body_api_service.dart` + models.
- [ ] `screens/evolution_screen.dart` — bodyweight hero + trend, 3-up stats, graph rows, evaluations history.
- [ ] `screens/eval_step_screen.dart` — 2-step manikin (circumferences then skinfolds), tap-to-enter measurement sheet, filled counter, finish → save.
- [ ] `screens/graph_detail_screen.dart` — metric segmented control, range selector, chart (`fl_chart`), highlights.
- [ ] Loading/error/empty states; plain `setState`.

**Technical notes** — Match design `screens-body.jsx` + `manikin.jsx`. The manikin can be an SVG/CustomPainter widget in `widgets/`.
