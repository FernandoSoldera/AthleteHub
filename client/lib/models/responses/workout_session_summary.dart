/// Slim workout-session view for the recent-sessions list — mirrors backend
/// `WorkoutSessionSummaryDto` (no exercises / sets, just the rollup fields).
class WorkoutSessionSummary {
  WorkoutSessionSummary({
    required this.id,
    required this.title,
    required this.status,
    required this.startedAt,
    required this.totalVolumeKg,
    required this.totalSets,
    required this.prCount,
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

  bool get isInProgress => status == 'in_progress';
  bool get isCompleted => status == 'completed';

  factory WorkoutSessionSummary.fromJson(Map<String, dynamic> json) {
    return WorkoutSessionSummary(
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
    );
  }
}
