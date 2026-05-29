import 'session_exercise.dart';

/// Full workout session view — used for start, get, patch, finish. Carries
/// the rollup fields (`totalVolumeKg`, `totalSets`, `prCount`) which stay
/// at zero until the finish-session pass on the backend computes them.
class WorkoutSession {
  WorkoutSession({
    required this.id,
    required this.title,
    required this.status,
    required this.startedAt,
    required this.totalVolumeKg,
    required this.totalSets,
    required this.prCount,
    required this.exercises,
    this.templateId,
    this.endedAt,
    this.durationSeconds,
  });

  final int id;
  final int? templateId;
  final String title;
  final String status;
  final DateTime startedAt;
  final DateTime? endedAt;
  final int? durationSeconds;
  final double totalVolumeKg;
  final int totalSets;
  final int prCount;
  final List<SessionExercise> exercises;

  bool get isInProgress => status == 'in_progress';
  bool get isCompleted => status == 'completed';

  factory WorkoutSession.fromJson(Map<String, dynamic> json) {
    final raw = (json['exercises'] as List<dynamic>? ?? const []);
    return WorkoutSession(
      id: (json['id'] as num).toInt(),
      templateId: (json['templateId'] as num?)?.toInt(),
      title: json['title'] as String,
      status: json['status'] as String,
      startedAt: DateTime.parse(json['startedAt'] as String),
      endedAt: json['endedAt'] == null
          ? null
          : DateTime.tryParse(json['endedAt'] as String),
      durationSeconds: (json['durationSeconds'] as num?)?.toInt(),
      totalVolumeKg: (json['totalVolumeKg'] as num?)?.toDouble() ?? 0,
      totalSets: (json['totalSets'] as num?)?.toInt() ?? 0,
      prCount: (json['prCount'] as num?)?.toInt() ?? 0,
      exercises: raw
          .map((e) => SessionExercise.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
