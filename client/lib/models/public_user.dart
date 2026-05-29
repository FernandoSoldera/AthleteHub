/// Public-safe view of another user. Mirrors backend `PublicUserDto` — no
/// email/age/height, which belong on the authenticated /me payload.
class PublicUser {
  PublicUser({
    required this.id,
    required this.fullName,
    required this.handle,
    this.avatarHue,
    this.bio,
  });

  final int id;
  final String fullName;
  final String handle;
  final int? avatarHue;
  final String? bio;

  factory PublicUser.fromJson(Map<String, dynamic> json) {
    return PublicUser(
      id: (json['id'] as num).toInt(),
      fullName: json['fullName'] as String,
      handle: json['handle'] as String,
      avatarHue: (json['avatarHue'] as num?)?.toInt(),
      bio: json['bio'] as String?,
    );
  }
}
