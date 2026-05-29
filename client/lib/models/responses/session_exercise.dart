import 'exercise_set.dart';

/// Exercise slot inside a workout session — carries the catalog name + the
/// materialized list of sets so the live workout screen reconstructs from
/// one GET. Mirrors backend `SessionExerciseDto`.
class SessionExercise {
  SessionExercise({
    required this.id,
    required this.exerciseId,
    required this.name,
    required this.position,
    required this.sets,
    this.scheme,
    this.targetWeight,
  });

  final int id;
  final int exerciseId;
  final String name;
  final int position;
  final String? scheme;
  final double? targetWeight;
  final List<ExerciseSet> sets;

  factory SessionExercise.fromJson(Map<String, dynamic> json) {
    final raw = (json['sets'] as List<dynamic>? ?? const []);
    return SessionExercise(
      id: (json['id'] as num).toInt(),
      exerciseId: (json['exerciseId'] as num).toInt(),
      name: json['name'] as String,
      position: (json['position'] as num).toInt(),
      scheme: json['scheme'] as String?,
      targetWeight: (json['targetWeight'] as num?)?.toDouble(),
      sets: raw
          .map((e) => ExerciseSet.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
