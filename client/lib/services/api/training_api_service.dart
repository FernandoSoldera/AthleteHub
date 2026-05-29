import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/cardio_activity.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/today_plan_response.dart';
import '../../models/responses/weekly_summary.dart';
import '../../models/responses/workout_session.dart';
import '../../models/responses/workout_session_summary.dart';
import 'http_interceptor.dart';

/// Training + cardio API: today's plan, weekly summary, sessions (start /
/// get / patch / finish / recent list), cardio (list / create). All
/// endpoints route through [HttpInterceptor] so 401 → silent refresh +
/// retry happens transparently for the caller.
class TrainingApiService {
  TrainingApiService._();

  // ── plan + summary ────────────────────────────────────────────────────

  static Future<TodayPlanResponse> today() async {
    final response = await HttpInterceptor.get('/api/training/today');
    _ensureOk(response);
    return TodayPlanResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<WeeklySummary> weeklySummary() async {
    final response = await HttpInterceptor.get('/api/training/weekly-summary');
    _ensureOk(response);
    return WeeklySummary.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── workout sessions ──────────────────────────────────────────────────

  static Future<CursorPage<WorkoutSessionSummary>> recentSessions({
    int? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/workout-sessions?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<WorkoutSessionSummary>(
      jsonDecode(response.body) as Map<String, dynamic>,
      WorkoutSessionSummary.fromJson,
    );
  }

  static Future<WorkoutSession> getSession(int id) async {
    final response = await HttpInterceptor.get('/api/workout-sessions/$id');
    _ensureOk(response);
    return WorkoutSession.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<WorkoutSession> startSession({int? templateId, String? title}) async {
    final body = <String, dynamic>{};
    if (templateId != null) body['templateId'] = templateId;
    if (title != null && title.isNotEmpty) body['title'] = title;
    final response = await HttpInterceptor.post('/api/workout-sessions', body: body);
    _ensureStatus(response, const [201]);
    return WorkoutSession.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Granular set ops batched into one PATCH. Each op carries
  /// `{ op: 'upsert' | 'delete', sessionExerciseId, setNumber, ... }`.
  /// The server applies them atomically and returns the updated session.
  static Future<WorkoutSession> patchSession(int sessionId, List<Map<String, dynamic>> sets) async {
    final response = await HttpInterceptor.patch(
      '/api/workout-sessions/$sessionId',
      body: {'sets': sets},
    );
    _ensureOk(response);
    return WorkoutSession.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<WorkoutSession> finishSession(int sessionId) async {
    final response = await HttpInterceptor.post('/api/workout-sessions/$sessionId/finish');
    _ensureOk(response);
    return WorkoutSession.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── cardio ────────────────────────────────────────────────────────────

  static Future<CursorPage<CardioActivity>> listCardio({int? cursor, int limit = 20}) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/cardio-activities?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<CardioActivity>(
      jsonDecode(response.body) as Map<String, dynamic>,
      CardioActivity.fromJson,
    );
  }

  static Future<CardioActivity> createCardio({
    required String type,
    required double distanceM,
    required int durationSeconds,
    double? avgPaceSPerKm,
    double? avgPowerW,
    int? avgHr,
    int? maxHr,
    double? elevationGainM,
    int? kcal,
    String? notes,
    DateTime? startedAt,
  }) async {
    final body = <String, dynamic>{
      'type': type,
      'distanceM': distanceM,
      'durationSeconds': durationSeconds,
    };
    if (avgPaceSPerKm != null) body['avgPaceSPerKm'] = avgPaceSPerKm;
    if (avgPowerW != null) body['avgPowerW'] = avgPowerW;
    if (avgHr != null) body['avgHr'] = avgHr;
    if (maxHr != null) body['maxHr'] = maxHr;
    if (elevationGainM != null) body['elevationGainM'] = elevationGainM;
    if (kcal != null) body['kcal'] = kcal;
    if (notes != null && notes.isNotEmpty) body['notes'] = notes;
    if (startedAt != null) body['startedAt'] = startedAt.toUtc().toIso8601String();

    final response = await HttpInterceptor.post('/api/cardio-activities', body: body);
    _ensureStatus(response, const [201]);
    return CardioActivity.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── helpers ───────────────────────────────────────────────────────────

  /// Build a `key=value&...` query string, skipping null/empty values.
  static String _qs(Map<String, String?> params) {
    final parts = <String>[];
    params.forEach((k, v) {
      if (v == null || v.isEmpty) return;
      parts.add('${Uri.encodeQueryComponent(k)}=${Uri.encodeQueryComponent(v)}');
    });
    return parts.join('&');
  }

  static void _ensureOk(http.Response response) => _ensureStatus(response, const [200]);

  static void _ensureStatus(http.Response response, List<int> allowed) {
    if (allowed.contains(response.statusCode)) return;
    ApiErrorResponse? err;
    try {
      final body = jsonDecode(response.body);
      if (body is Map<String, dynamic>) {
        err = ApiErrorResponse.fromJson(response.statusCode, body);
      }
    } catch (_) {}
    throw ApiException(
      statusCode: response.statusCode,
      code: err?.code,
      message: err?.message,
      fieldErrors: err?.fieldErrors,
    );
  }
}
