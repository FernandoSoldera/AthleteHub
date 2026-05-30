import '../public_user.dart';

/// One row on a coach's roster — relationship metadata + the athlete
/// hydrated. Mirrors backend `RosterEntryDto`. `flag` / `adherencePct` /
/// `lastActivityAt` stay null until Epic 9's rollups land; the UI shows a
/// dash for null values.
class RosterEntry {
  RosterEntry({
    required this.id,
    required this.status,
    required this.athlete,
    this.since,
    this.goal,
    this.flag,
    this.adherencePct,
    this.lastActivityAt,
  });

  final int id;
  final String status;
  final DateTime? since;
  final String? goal;
  final String? flag;
  final int? adherencePct;
  final DateTime? lastActivityAt;
  final PublicUser athlete;

  factory RosterEntry.fromJson(Map<String, dynamic> json) {
    return RosterEntry(
      id: (json['id'] as num).toInt(),
      status: json['status'] as String,
      since: json['since'] == null ? null : DateTime.parse(json['since'] as String),
      goal: json['goal'] as String?,
      flag: json['flag'] as String?,
      adherencePct: (json['adherencePct'] as num?)?.toInt(),
      lastActivityAt: json['lastActivityAt'] == null
          ? null
          : DateTime.tryParse(json['lastActivityAt'] as String),
      athlete: PublicUser.fromJson(json['athlete'] as Map<String, dynamic>),
    );
  }
}
