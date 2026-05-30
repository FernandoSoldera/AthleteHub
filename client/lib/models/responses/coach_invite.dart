import '../public_user.dart';

/// One coach↔athlete relationship row with both sides hydrated.
/// Mirrors backend `CoachInviteDto` — returned by the invite create + inbox
/// endpoints. `coach` / `athlete` may be null in edge cases (deleted account),
/// but normally both are present.
class CoachInvite {
  CoachInvite({
    required this.id,
    required this.status,
    required this.since,
    required this.createdAt,
    this.coach,
    this.athlete,
  });

  final int id;
  final String status;
  final DateTime? since;
  final DateTime createdAt;
  final PublicUser? coach;
  final PublicUser? athlete;

  factory CoachInvite.fromJson(Map<String, dynamic> json) {
    return CoachInvite(
      id: (json['id'] as num).toInt(),
      status: json['status'] as String,
      since: json['since'] == null ? null : DateTime.parse(json['since'] as String),
      createdAt: DateTime.parse(json['createdAt'] as String),
      coach: json['coach'] == null
          ? null
          : PublicUser.fromJson(json['coach'] as Map<String, dynamic>),
      athlete: json['athlete'] == null
          ? null
          : PublicUser.fromJson(json['athlete'] as Map<String, dynamic>),
    );
  }
}
