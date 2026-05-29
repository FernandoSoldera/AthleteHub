import 'template_exercise.dart';

/// A reusable workout plan with its ordered exercise list materialized.
/// Mirrors backend `WorkoutTemplateDto` — used as the `template` field of
/// `TodayPlanResponse`.
class WorkoutTemplate {
  WorkoutTemplate({
    required this.id,
    required this.name,
    required this.exercises,
    this.description,
  });

  final int id;
  final String name;
  final String? description;
  final List<TemplateExercise> exercises;

  factory WorkoutTemplate.fromJson(Map<String, dynamic> json) {
    final raw = (json['exercises'] as List<dynamic>? ?? const []);
    return WorkoutTemplate(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      description: json['description'] as String?,
      exercises: raw
          .map((e) => TemplateExercise.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
