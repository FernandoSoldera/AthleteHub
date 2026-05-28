import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Reads runtime configuration from .env (loaded once at startup in main()).
/// Add derived URLs here as endpoints land — keep this the single place that
/// knows the backend layout.
class AppConfig {
  static String get apiBaseUrl =>
      dotenv.env['API_BASE_URL'] ?? 'http://10.0.2.2:8080';

  // Derived endpoint roots (populate as EPIC 1+ stories add them).
  static String get authApiUrl => '$apiBaseUrl/api/auth';
  static String get usersApiUrl => '$apiBaseUrl/api/users';
}
