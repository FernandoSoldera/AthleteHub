import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Token + cached profile storage backed by Keychain (iOS) / EncryptedSharedPrefs
/// (Android). The access token is short-lived; the refresh token lets us mint a
/// new pair without re-prompting the user. The cached user JSON is just a
/// convenience so the splash screen can render an empty shell without waiting
/// on `/api/me`.
class SecureStorageService {
  SecureStorageService._();

  static const _kAccessToken = 'auth.access_token';
  static const _kRefreshToken = 'auth.refresh_token';
  static const _kUserJson = 'auth.user_json';

  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _kAccessToken, value: accessToken);
    await _storage.write(key: _kRefreshToken, value: refreshToken);
  }

  static Future<String?> getAccessToken() => _storage.read(key: _kAccessToken);

  static Future<String?> getRefreshToken() => _storage.read(key: _kRefreshToken);

  static Future<void> saveUserJson(String json) =>
      _storage.write(key: _kUserJson, value: json);

  static Future<String?> getUserJson() => _storage.read(key: _kUserJson);

  static Future<void> clear() async {
    await _storage.delete(key: _kAccessToken);
    await _storage.delete(key: _kRefreshToken);
    await _storage.delete(key: _kUserJson);
  }

  static Future<bool> hasSession() async {
    final access = await getAccessToken();
    final refresh = await getRefreshToken();
    return access != null && refresh != null;
  }
}
