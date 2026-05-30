import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../config/app_config.dart';
import '../../models/responses/auth_response.dart';
import '../secure_storage_service.dart';

/// Centralized HTTP wrapper for the app's backend calls. Responsibilities:
///
/// 1. Attach the stored access token as `Authorization: Bearer …` when one
///    exists (pre-auth endpoints like /api/auth/login go through with no token
///    and never trigger the 401 dance).
/// 2. On a 401 to a request that *had* a token, transparently call
///    `/api/auth/token/refresh` once, swap in the new pair, and retry — the
///    caller never sees the expired-token blip.
/// 3. If the refresh itself fails (revoked / expired / reused), wipe local
///    auth state and notify the app via [onUnauthorized] so it can route to
///    the login screen.
///
/// The refresh call uses the raw `http` package so it can't recurse through
/// this same interceptor.
class HttpInterceptor {
  HttpInterceptor._();

  /// Set by `main.dart` to route to the login screen when the session is
  /// definitively dead.
  static void Function()? onUnauthorized;

  // ── public verbs ───────────────────────────────────────────────────────

  static Future<http.Response> get(String path,
          {Map<String, String>? headers}) =>
      _send('GET', path, headers: headers);

  static Future<http.Response> post(String path,
          {Map<String, String>? headers, Object? body}) =>
      _send('POST', path, headers: headers, body: body);

  static Future<http.Response> patch(String path,
          {Map<String, String>? headers, Object? body}) =>
      _send('PATCH', path, headers: headers, body: body);

  static Future<http.Response> put(String path,
          {Map<String, String>? headers, Object? body}) =>
      _send('PUT', path, headers: headers, body: body);

  static Future<http.Response> delete(String path,
          {Map<String, String>? headers}) =>
      _send('DELETE', path, headers: headers);

  // ── internals ──────────────────────────────────────────────────────────

  static Future<http.Response> _send(
    String method,
    String path, {
    Map<String, String>? headers,
    Object? body,
  }) async {
    final url = Uri.parse('${AppConfig.apiBaseUrl}$path');
    final h = await _buildHeaders(headers);
    final hadToken = h.containsKey('Authorization');

    var response = await _doRequest(method, url, h, body);

    if (response.statusCode == 401 && hadToken) {
      final refreshed = await _tryRefresh();
      if (refreshed) {
        final h2 = await _buildHeaders(headers); // re-read the rotated access token
        response = await _doRequest(method, url, h2, body);
      } else {
        onUnauthorized?.call();
      }
    }
    return response;
  }

  static Future<Map<String, String>> _buildHeaders(
      Map<String, String>? extra) async {
    final h = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...?extra,
    };
    final token = await SecureStorageService.getAccessToken();
    if (token != null) h['Authorization'] = 'Bearer $token';
    return h;
  }

  static Future<http.Response> _doRequest(
    String method,
    Uri url,
    Map<String, String> headers,
    Object? body,
  ) {
    final encoded =
        body == null ? null : (body is String ? body : jsonEncode(body));
    switch (method) {
      case 'GET':
        return http.get(url, headers: headers);
      case 'POST':
        return http.post(url, headers: headers, body: encoded);
      case 'PATCH':
        return http.patch(url, headers: headers, body: encoded);
      case 'PUT':
        return http.put(url, headers: headers, body: encoded);
      case 'DELETE':
        return http.delete(url, headers: headers);
      default:
        throw UnsupportedError('Unsupported HTTP method: $method');
    }
  }

  /// Tries the refresh endpoint once. Returns true on success (tokens are
  /// already persisted), false otherwise (and clears local auth state).
  static Future<bool> _tryRefresh() async {
    final refreshToken = await SecureStorageService.getRefreshToken();
    if (refreshToken == null) return false;

    try {
      final response = await http.post(
        Uri.parse('${AppConfig.apiBaseUrl}/api/auth/token/refresh'),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode({'refreshToken': refreshToken}),
      );

      if (response.statusCode != 200) {
        await SecureStorageService.clear();
        return false;
      }

      final auth = AuthResponse.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>);
      await SecureStorageService.saveTokens(
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
      );
      await SecureStorageService.saveUserJson(jsonEncode(auth.user.toJson()));
      return true;
    } catch (_) {
      // Network failure during refresh — fall back to forcing a re-login.
      await SecureStorageService.clear();
      return false;
    }
  }
}
