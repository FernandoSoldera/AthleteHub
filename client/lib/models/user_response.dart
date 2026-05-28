/// The authenticated user — what `GET /api/me` and login responses carry. Manual
/// (de)serialization to keep the build simple per CONVENTIONS (no codegen).
class UserResponse {
  UserResponse({
    required this.id,
    required this.email,
    required this.fullName,
    required this.handle,
    required this.roles,
    this.avatarHue,
    this.bio,
    this.age,
    this.heightCm,
    this.dateJoined,
  });

  final int id;
  final String email;
  final String fullName;
  final String handle;
  final List<String> roles;
  final int? avatarHue;
  final String? bio;
  final int? age;
  final double? heightCm;
  final String? dateJoined;

  factory UserResponse.fromJson(Map<String, dynamic> json) {
    return UserResponse(
      id: (json['id'] as num).toInt(),
      email: json['email'] as String,
      fullName: json['fullName'] as String,
      handle: json['handle'] as String,
      roles: (json['roles'] as List<dynamic>? ?? const [])
          .map((e) => e as String)
          .toList(),
      avatarHue: (json['avatarHue'] as num?)?.toInt(),
      bio: json['bio'] as String?,
      age: (json['age'] as num?)?.toInt(),
      heightCm: (json['heightCm'] as num?)?.toDouble(),
      dateJoined: json['dateJoined'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'fullName': fullName,
        'handle': handle,
        'roles': roles,
        if (avatarHue != null) 'avatarHue': avatarHue,
        if (bio != null) 'bio': bio,
        if (age != null) 'age': age,
        if (heightCm != null) 'heightCm': heightCm,
        if (dateJoined != null) 'dateJoined': dateJoined,
      };

  bool get isAthlete => roles.contains('ATHLETE');
  bool get isCoach => roles.contains('COACH');
}
