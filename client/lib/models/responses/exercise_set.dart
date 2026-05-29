/// One set in a live or completed workout. Mirrors backend `ExerciseSetDto`.
/// `pr` is set by the finish-session pass; while a session is in-progress
/// every set's `pr` is false.
class ExerciseSet {
  ExerciseSet({
    required this.id,
    required this.sessionExerciseId,
    required this.setNumber,
    required this.done,
    required this.pr,
    this.weightKg,
    this.reps,
    this.rpe,
    this.completedAt,
  });

  final int id;
  final int sessionExerciseId;
  final int setNumber;
  final double? weightKg;
  final int? reps;
  final double? rpe;
  final bool done;
  final bool pr;
  final DateTime? completedAt;

  factory ExerciseSet.fromJson(Map<String, dynamic> json) {
    return ExerciseSet(
      id: (json['id'] as num).toInt(),
      sessionExerciseId: (json['sessionExerciseId'] as num).toInt(),
      setNumber: (json['setNumber'] as num).toInt(),
      weightKg: (json['weightKg'] as num?)?.toDouble(),
      reps: (json['reps'] as num?)?.toInt(),
      rpe: (json['rpe'] as num?)?.toDouble(),
      done: json['done'] as bool? ?? false,
      pr: json['pr'] as bool? ?? false,
      completedAt: json['completedAt'] == null
          ? null
          : DateTime.tryParse(json['completedAt'] as String),
    );
  }
}
