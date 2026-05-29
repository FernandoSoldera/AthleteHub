import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/day_response.dart';
import '../../models/responses/diary_entry.dart';
import '../../models/responses/diet_plan.dart';
import '../../models/responses/favorite.dart';
import '../../models/responses/food.dart';
import 'http_interceptor.dart';

/// Diet endpoints (AH-052/053) + food search (AH-051). All routes use
/// [HttpInterceptor] so 401 → silent refresh + retry happens transparently.
class DietApiService {
  DietApiService._();

  // ── day + active plan ─────────────────────────────────────────────────

  static Future<DayResponse> day({DateTime? date}) async {
    final qs = _qs({'date': date == null ? null : _formatDate(date)});
    final url = qs.isEmpty ? '/api/diet/day' : '/api/diet/day?$qs';
    final response = await HttpInterceptor.get(url);
    _ensureOk(response);
    return DayResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Returns the hydrated active plan or null when none is set. The
  /// server emits an empty body for null; we treat any non-2xx as a
  /// thrown [ApiException] and any 200 with empty/null body as a null
  /// plan.
  static Future<DietPlan?> getActivePlan() async {
    final response = await HttpInterceptor.get('/api/diet/active');
    _ensureOk(response);
    final body = response.body.trim();
    if (body.isEmpty || body == 'null') return null;
    return DietPlan.fromJson(jsonDecode(body) as Map<String, dynamic>);
  }

  // ── diary CRUD ────────────────────────────────────────────────────────

  static Future<DiaryEntry> addDiaryEntry({
    required int foodId,
    required double amount,
    required String unit,
    String? mealLabel,
    DateTime? eatenAt,
    String? source,
  }) async {
    final body = <String, dynamic>{
      'foodId': foodId,
      'amount': amount,
      'unit': unit,
    };
    if (mealLabel != null && mealLabel.isNotEmpty) body['mealLabel'] = mealLabel;
    if (eatenAt != null) body['eatenAt'] = eatenAt.toUtc().toIso8601String();
    if (source != null) body['source'] = source;
    final response = await HttpInterceptor.post('/api/diet/diary', body: body);
    _ensureStatus(response, const [201]);
    return DiaryEntry.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> deleteDiaryEntry(int id) async {
    final response = await HttpInterceptor.delete('/api/diet/diary/$id');
    _ensureStatus(response, const [204]);
  }

  // ── favorites ─────────────────────────────────────────────────────────

  static Future<CursorPage<Favorite>> listFavorites({int? cursor, int limit = 50}) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/diet/favorites?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<Favorite>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Favorite.fromJson,
    );
  }

  static Future<Favorite> addFavorite(int foodId) async {
    final response = await HttpInterceptor.post(
      '/api/diet/favorites',
      body: {'foodId': foodId},
    );
    _ensureStatus(response, const [201]);
    return Favorite.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> removeFavorite(int foodId) async {
    final response = await HttpInterceptor.delete('/api/diet/favorites/$foodId');
    _ensureStatus(response, const [204]);
  }

  // ── food search ───────────────────────────────────────────────────────

  static Future<CursorPage<Food>> searchFoods({
    String? q,
    int? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'q': q, 'cursor': cursor?.toString(), 'limit': '$limit'});
    final url = qs.isEmpty ? '/api/foods' : '/api/foods?$qs';
    final response = await HttpInterceptor.get(url);
    _ensureOk(response);
    return CursorPage.fromJson<Food>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Food.fromJson,
    );
  }

  // ── helpers ───────────────────────────────────────────────────────────

  static String _formatDate(DateTime date) {
    final y = date.year.toString().padLeft(4, '0');
    final m = date.month.toString().padLeft(2, '0');
    final d = date.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
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
