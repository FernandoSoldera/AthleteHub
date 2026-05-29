/// One slot in a workout template — exercise + scheme + target. Mirrors
/// backend `TemplateExerciseDto`. The catalog name is hydrated server-side
/// so the Train hero card can render without an extra round-trip.
class TemplateExercise {
  TemplateExercise({
    required this.exerciseId,
    required this.name,
    required this.position,
    this.scheme,
    this.target,
  });

  final int exerciseId;
  final String name;
  final int position;
  final String? scheme;
  final String? target;

  factory TemplateExercise.fromJson(Map<String, dynamic> json) {
    return TemplateExercise(
      exerciseId: (json['exerciseId'] as num).toInt(),
      name: json['name'] as String,
      position: (json['position'] as num).toInt(),
      scheme: json['scheme'] as String?,
      target: json['target'] as String?,
    );
  }
}
