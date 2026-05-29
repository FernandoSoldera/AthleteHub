import '../public_user.dart';

/// Aggregate response from `GET /api/users/{handle}` — header + counters +
/// the viewer-scoped follow flag.
class PublicProfileResponse {
  PublicProfileResponse({
    required this.user,
    required this.followers,
    required this.following,
    required this.iFollow,
  });

  final PublicUser user;
  final int followers;
  final int following;
  final bool iFollow;

  factory PublicProfileResponse.fromJson(Map<String, dynamic> json) {
    return PublicProfileResponse(
      user: PublicUser.fromJson(json['user'] as Map<String, dynamic>),
      followers: (json['followers'] as num?)?.toInt() ?? 0,
      following: (json['following'] as num?)?.toInt() ?? 0,
      iFollow: json['iFollow'] as bool? ?? false,
    );
  }
}
