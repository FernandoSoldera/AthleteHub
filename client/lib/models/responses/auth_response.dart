import '../user_response.dart';

/// Payload returned by `POST /api/auth/login`, `/oauth/{provider}`, and
/// `/token/refresh`. Mirrors backend `AuthResponse`.
class AuthResponse {
  AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.accessTokenExpiresIn,
    required this.tokenType,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final int accessTokenExpiresIn;
  final String tokenType;
  final UserResponse user;

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      accessTokenExpiresIn: (json['accessTokenExpiresIn'] as num).toInt(),
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      user: UserResponse.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}
