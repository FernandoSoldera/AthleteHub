import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/assignment.dart';
import '../../models/responses/coach_invite.dart';
import '../../models/responses/coach_profile.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/my_coach.dart';
import '../../models/responses/roster_entry.dart';
import '../../models/responses/student_detail.dart';
import 'http_interceptor.dart';

/// Coaching API surface — invites (AH-071), roster + my-coach (AH-072),
/// student detail (AH-073), assignments (AH-074), and coach-profile
/// upsert (AH-075). All endpoints require auth.
class CoachApiService {
  CoachApiService._();

  // ── invites (AH-071) ───────────────────────────────────────────────────

  static Future<CoachInvite> sendInvite(String handle) async {
    final response = await HttpInterceptor.post(
      '/api/coach/invites',
      body: {'handle': handle},
    );
    _ensureStatus(response, const [200, 201]);
    return CoachInvite.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<List<CoachInvite>> myInvites() async {
    final response = await HttpInterceptor.get('/api/me/coach-invites');
    _ensureOk(response);
    final list = jsonDecode(response.body) as List;
    return list
        .map((e) => CoachInvite.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  static Future<CoachInvite> acceptInvite(int id) async {
    final response =
        await HttpInterceptor.post('/api/me/coach-invites/$id/accept');
    _ensureOk(response);
    return CoachInvite.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<CoachInvite> declineInvite(int id) async {
    final response =
        await HttpInterceptor.post('/api/me/coach-invites/$id/decline');
    _ensureOk(response);
    return CoachInvite.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── roster + my-coach (AH-072) ─────────────────────────────────────────

  static Future<CursorPage<RosterEntry>> roster({
    String? status,
    String? flag,
    String? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({
      'status': status,
      'flag': flag,
      'cursor': cursor,
      'limit': '$limit',
    });
    final response = await HttpInterceptor.get('/api/coach/athletes?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<RosterEntry>(
      jsonDecode(response.body) as Map<String, dynamic>,
      RosterEntry.fromJson,
    );
  }

  /// `GET /api/me/coach` — returns null when the athlete has no active coach
  /// (Spring writes an empty body for a null return value).
  static Future<MyCoach?> myCoach() async {
    final response = await HttpInterceptor.get('/api/me/coach');
    _ensureOk(response);
    final body = response.body.trim();
    if (body.isEmpty || body == 'null') return null;
    final decoded = jsonDecode(body);
    if (decoded == null) return null;
    return MyCoach.fromJson(decoded as Map<String, dynamic>);
  }

  // ── student detail (AH-073) ────────────────────────────────────────────

  static Future<StudentDetail> studentDetail(int athleteId) async {
    final response = await HttpInterceptor.get('/api/coach/athletes/$athleteId');
    _ensureOk(response);
    return StudentDetail.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── assignments (AH-074) ───────────────────────────────────────────────

  static Future<Assignment> createAssignment({
    required int athleteId,
    required String type,
    String? refType,
    int? refId,
    DateTime? scheduledFor,
    String? notes,
  }) async {
    final body = <String, dynamic>{'type': type};
    if (refType != null) body['refType'] = refType;
    if (refId != null) body['refId'] = refId;
    if (scheduledFor != null) {
      body['scheduledFor'] = _isoDate(scheduledFor);
    }
    if (notes != null && notes.isNotEmpty) body['notes'] = notes;

    final response = await HttpInterceptor.post(
      '/api/coach/athletes/$athleteId/assignments',
      body: body,
    );
    _ensureStatus(response, const [200, 201]);
    return Assignment.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<CursorPage<Assignment>> assignmentsForAthlete({
    required int athleteId,
    String? status,
    DateTime? scheduledOn,
    String? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({
      'status': status,
      'scheduledOn': scheduledOn == null ? null : _isoDate(scheduledOn),
      'cursor': cursor,
      'limit': '$limit',
    });
    final response = await HttpInterceptor.get(
        '/api/coach/athletes/$athleteId/assignments?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<Assignment>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Assignment.fromJson,
    );
  }

  static Future<Assignment> patchAssignment({
    required int id,
    String? status,
    DateTime? scheduledFor,
    String? notes,
  }) async {
    final body = <String, dynamic>{};
    if (status != null) body['status'] = status;
    if (scheduledFor != null) body['scheduledFor'] = _isoDate(scheduledFor);
    if (notes != null) body['notes'] = notes;

    final response =
        await HttpInterceptor.patch('/api/coach/assignments/$id', body: body);
    _ensureOk(response);
    return Assignment.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> deleteAssignment(int id) async {
    final response =
        await HttpInterceptor.delete('/api/coach/assignments/$id');
    _ensureStatus(response, const [204]);
  }

  static Future<CursorPage<Assignment>> myAssignments({
    String? status,
    DateTime? scheduledOn,
    String? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({
      'status': status,
      'scheduledOn': scheduledOn == null ? null : _isoDate(scheduledOn),
      'cursor': cursor,
      'limit': '$limit',
    });
    final response = await HttpInterceptor.get('/api/me/assignments?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<Assignment>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Assignment.fromJson,
    );
  }

  // ── coach profile (AH-075) ─────────────────────────────────────────────

  static Future<CoachProfile> myCoachProfile() async {
    final response = await HttpInterceptor.get('/api/me/coach-profile');
    _ensureOk(response);
    return CoachProfile.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<CoachProfile> updateMyCoachProfile({
    String? headline,
    int? yearsExperience,
  }) async {
    final body = <String, dynamic>{};
    if (headline != null) body['headline'] = headline;
    if (yearsExperience != null) body['yearsExperience'] = yearsExperience;

    final response =
        await HttpInterceptor.put('/api/me/coach-profile', body: body);
    _ensureOk(response);
    return CoachProfile.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  static String _isoDate(DateTime d) {
    final y = d.year.toString().padLeft(4, '0');
    final m = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '$y-$m-$day';
  }

  static String _qs(Map<String, String?> params) {
    final parts = <String>[];
    params.forEach((k, v) {
      if (v == null || v.isEmpty) return;
      parts.add('${Uri.encodeQueryComponent(k)}=${Uri.encodeQueryComponent(v)}');
    });
    return parts.join('&');
  }

  static void _ensureOk(http.Response response) =>
      _ensureStatus(response, const [200]);

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
