import '../public_user.dart';
import 'evaluation_summary.dart';
import 'weekly_summary.dart';
import 'workout_session_summary.dart';

/// Coach's deep-dive view of one athlete — relationship metadata + recent
/// rollups. Mirrors backend `StudentDetailDto`.
///
/// `latestEvaluation` is null when the athlete has never logged one;
/// `weeklyCardio` is never null (zeros when no cardio in the window);
/// `recentSessions` is empty when the athlete hasn't trained yet.
class StudentDetail {
  StudentDetail({
    required this.relationshipId,
    required this.status,
    required this.athlete,
    required this.weeklyCardio,
    required this.recentSessions,
    this.since,
    this.goal,
    this.flag,
    this.adherencePct,
    this.lastActivityAt,
    this.latestEvaluation,
  });

  final int relationshipId;
  final String status;
  final DateTime? since;
  final String? goal;
  final String? flag;
  final int? adherencePct;
  final DateTime? lastActivityAt;
  final PublicUser athlete;
  final EvaluationSummary? latestEvaluation;
  final WeeklySummary weeklyCardio;
  final List<WorkoutSessionSummary> recentSessions;

  factory StudentDetail.fromJson(Map<String, dynamic> json) {
    return StudentDetail(
      relationshipId: (json['relationshipId'] as num).toInt(),
      status: json['status'] as String,
      since: json['since'] == null ? null : DateTime.parse(json['since'] as String),
      goal: json['goal'] as String?,
      flag: json['flag'] as String?,
      adherencePct: (json['adherencePct'] as num?)?.toInt(),
      lastActivityAt: json['lastActivityAt'] == null
          ? null
          : DateTime.tryParse(json['lastActivityAt'] as String),
      athlete: PublicUser.fromJson(json['athlete'] as Map<String, dynamic>),
      latestEvaluation: json['latestEvaluation'] == null
          ? null
          : EvaluationSummary.fromJson(
              json['latestEvaluation'] as Map<String, dynamic>),
      weeklyCardio: WeeklySummary.fromJson(
          (json['weeklyCardio'] as Map<String, dynamic>?) ?? const {}),
      recentSessions: ((json['recentSessions'] as List?) ?? const [])
          .map((e) => WorkoutSessionSummary.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}
