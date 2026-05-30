import '../public_user.dart';

/// Athlete-side view of the active coach relationship — mirrors backend
/// `MyCoachDto`. The endpoint returns 200 with this object or 200 with an
/// empty/null body when the athlete has no active coach; the service maps
/// the empty case → null.
class MyCoach {
  MyCoach({
    required this.id,
    required this.status,
    required this.coach,
    this.since,
    this.goal,
  });

  final int id;
  final String status;
  final DateTime? since;
  final String? goal;
  final PublicUser coach;

  factory MyCoach.fromJson(Map<String, dynamic> json) {
    return MyCoach(
      id: (json['id'] as num).toInt(),
      status: json['status'] as String,
      since: json['since'] == null ? null : DateTime.parse(json['since'] as String),
      goal: json['goal'] as String?,
      coach: PublicUser.fromJson(json['coach'] as Map<String, dynamic>),
    );
  }
}
