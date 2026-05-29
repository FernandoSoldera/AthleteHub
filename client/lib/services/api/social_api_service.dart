import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/public_user.dart';
import '../../models/responses/api_error_response.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/public_profile_response.dart';
import '../../models/responses/suggested_user.dart';
import 'http_interceptor.dart';

/// Social-graph API: search, suggestions, profile lookup, follow/unfollow,
/// and followers/following lists. All endpoints require auth — the
/// HttpInterceptor attaches the access token and refreshes on 401.
class SocialApiService {
  SocialApiService._();

  // ── search + suggestions ───────────────────────────────────────────────

  static Future<CursorPage<PublicUser>> search(String q, {String? cursor, int limit = 20}) async {
    final qs = _qs({'q': q, 'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/users/search?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<PublicUser>(
      jsonDecode(response.body) as Map<String, dynamic>,
      PublicUser.fromJson,
    );
  }

  static Future<CursorPage<SuggestedUser>> suggestions({String? cursor, int limit = 20}) async {
    final qs = _qs({'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/users/suggestions?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<SuggestedUser>(
      jsonDecode(response.body) as Map<String, dynamic>,
      SuggestedUser.fromJson,
    );
  }

  // ── public profile + follow graph ──────────────────────────────────────

  static Future<PublicProfileResponse> profileByHandle(String handle) async {
    final response = await HttpInterceptor.get('/api/users/$handle');
    _ensureOk(response);
    return PublicProfileResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> follow(int userId) async {
    final response = await HttpInterceptor.post('/api/users/$userId/follow');
    _ensureStatus(response, const [204]);
  }

  static Future<void> unfollow(int userId) async {
    final response = await HttpInterceptor.delete('/api/users/$userId/follow');
    _ensureStatus(response, const [204]);
  }

  static Future<CursorPage<PublicUser>> myFollowers({String? cursor, int limit = 20}) async {
    final qs = _qs({'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/me/followers?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<PublicUser>(
      jsonDecode(response.body) as Map<String, dynamic>,
      PublicUser.fromJson,
    );
  }

  static Future<CursorPage<PublicUser>> myFollowing({String? cursor, int limit = 20}) async {
    final qs = _qs({'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/me/following?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<PublicUser>(
      jsonDecode(response.body) as Map<String, dynamic>,
      PublicUser.fromJson,
    );
  }

  // ── helpers ────────────────────────────────────────────────────────────

  /// Build a `key=value&key=value` query string from an entries map, skipping
  /// nulls and empty values.
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
