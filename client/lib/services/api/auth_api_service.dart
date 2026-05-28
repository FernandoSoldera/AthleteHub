import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/auth_response.dart';
import '../../models/user_response.dart';
import '../secure_storage_service.dart';
import 'http_interceptor.dart';

/// Auth-flow API surface. Persists tokens + cached user on success; surfaces
/// backend domain errors as [ApiException] so screens can render them via
/// `AppLocalizations.translateErrorCode(ex.code)`.
class AuthApiService {
  AuthApiService._();

  static Future<UserResponse> register({
    required String email,
    required String password,
    required String fullName,
    required String handle,
  }) async {
    final response = await HttpInterceptor.post('/api/auth/register', body: {
      'email': email,
      'password': password,
      'fullName': fullName,
      'handle': handle,
    });
    _ensureStatus(response, const [201]);
    return UserResponse.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<AuthResponse> login({
    required String email,
    required String password,
  }) async {
    final response = await HttpInterceptor.post('/api/auth/login', body: {
      'email': email,
      'password': password,
    });
    _ensureStatus(response, const [200]);
    return _persistAndReturn(response);
  }

  static Future<AuthResponse> oauthLogin({
    required String provider, // 'google' | 'apple'
    required String idToken,
  }) async {
    final response = await HttpInterceptor.post('/api/auth/oauth/$provider',
        body: {'idToken': idToken});
    _ensureStatus(response, const [200]);
    return _persistAndReturn(response);
  }

  /// Best-effort server-side revoke followed by a local wipe. Always clears
  /// local state — a network error here shouldn't leave the user "stuck
  /// logged in" client-side.
  static Future<void> logout() async {
    final refreshToken = await SecureStorageService.getRefreshToken();
    if (refreshToken != null) {
      try {
        await HttpInterceptor.post('/api/auth/logout',
            body: {'refreshToken': refreshToken});
      } catch (_) {
        // ignore — local clear below is what matters
      }
    }
    await SecureStorageService.clear();
  }

  static Future<void> forgotPassword(String email) async {
    final response = await HttpInterceptor.post('/api/auth/password/forgot',
        body: {'email': email});
    _ensureStatus(response, const [202]);
  }

  static Future<void> resetPassword({
    required String code,
    required String password,
  }) async {
    final response = await HttpInterceptor.post('/api/auth/password/reset',
        body: {'code': code, 'password': password});
    _ensureStatus(response, const [204]);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  static Future<AuthResponse> _persistAndReturn(http.Response response) async {
    final auth = AuthResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
    await SecureStorageService.saveTokens(
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
    );
    await SecureStorageService.saveUserJson(jsonEncode(auth.user.toJson()));
    return auth;
  }

  static void _ensureStatus(http.Response response, List<int> allowed) {
    if (allowed.contains(response.statusCode)) return;

    ApiErrorResponse? err;
    try {
      final body = jsonDecode(response.body);
      if (body is Map<String, dynamic>) {
        err = ApiErrorResponse.fromJson(response.statusCode, body);
      }
    } catch (_) {
      // body wasn't JSON; fall through to a bare ApiException
    }
    throw ApiException(
      statusCode: response.statusCode,
      code: err?.code,
      message: err?.message,
      fieldErrors: err?.fieldErrors,
    );
  }
}
