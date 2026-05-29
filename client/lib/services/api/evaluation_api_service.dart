import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/evaluation.dart';
import '../../models/responses/evaluation_measurement.dart';
import '../../models/responses/evaluation_summary.dart';
import '../../models/responses/metric_series.dart';
import 'http_interceptor.dart';

/// Body / Evolution API (AH-041, AH-042). All endpoints route through
/// [HttpInterceptor] so 401 → silent refresh + retry happens transparently.
class EvaluationApiService {
  EvaluationApiService._();

  // ── list / get / create ───────────────────────────────────────────────

  static Future<CursorPage<EvaluationSummary>> listRecent({
    int? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/evaluations?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<EvaluationSummary>(
      jsonDecode(response.body) as Map<String, dynamic>,
      EvaluationSummary.fromJson,
    );
  }

  static Future<Evaluation> getById(int id) async {
    final response = await HttpInterceptor.get('/api/evaluations/$id');
    _ensureOk(response);
    return Evaluation.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Body-fat method semantics:
  ///   * null         → weight-only check-in (bodyFatPct / bfMethod stay null)
  ///   * 'manual'     → server stores bodyFatPct as-is (required)
  ///   * 'jackson_pollock_7' / 'navy' → server computes from measurements +
  ///     user profile (sex/age/height); bodyFatPct param is ignored
  static Future<Evaluation> create({
    required double weightKg,
    String? bfMethod,
    double? bodyFatPct,
    String? notes,
    DateTime? evaluatedAt,
    List<EvaluationMeasurement> measurements = const [],
  }) async {
    final body = <String, dynamic>{
      'weightKg': weightKg,
    };
    if (bfMethod != null) body['bfMethod'] = bfMethod;
    if (bodyFatPct != null) body['bodyFatPct'] = bodyFatPct;
    if (notes != null && notes.isNotEmpty) body['notes'] = notes;
    if (evaluatedAt != null) body['evaluatedAt'] = evaluatedAt.toUtc().toIso8601String();
    if (measurements.isNotEmpty) {
      body['measurements'] = measurements.map((m) => m.toJson()).toList();
    }
    final response = await HttpInterceptor.post('/api/evaluations', body: body);
    _ensureStatus(response, const [201]);
    return Evaluation.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── metric series ─────────────────────────────────────────────────────

  /// `metric`: `weight`, `body_fat`, or any free-form `point_id`.
  /// `range`: `4w`, `12w`, `6m`, `1y`.
  static Future<MetricSeries> getSeries({
    required String metric,
    required String range,
  }) async {
    final qs = _qs({'metric': metric, 'range': range});
    final response = await HttpInterceptor.get('/api/body/series?$qs');
    _ensureOk(response);
    return MetricSeries.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── helpers ───────────────────────────────────────────────────────────

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
