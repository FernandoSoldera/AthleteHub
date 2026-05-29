/// Suggested user with mutual-follow count. Mirrors backend `SuggestedUserDto`.
class SuggestedUser {
  SuggestedUser({
    required this.id,
    required this.fullName,
    required this.handle,
    required this.mutualCount,
    this.avatarHue,
    this.bio,
  });

  final int id;
  final String fullName;
  final String handle;
  final int mutualCount;
  final int? avatarHue;
  final String? bio;

  factory SuggestedUser.fromJson(Map<String, dynamic> json) {
    return SuggestedUser(
      id: (json['id'] as num).toInt(),
      fullName: json['fullName'] as String,
      handle: json['handle'] as String,
      mutualCount: (json['mutualCount'] as num?)?.toInt() ?? 0,
      avatarHue: (json['avatarHue'] as num?)?.toInt(),
      bio: json['bio'] as String?,
    );
  }
}
