import 'diet_meal.dart';
import 'macros.dart';

/// A diet plan with meals → items → foods materialized + the daily target.
class DietPlan {
  DietPlan({
    required this.id,
    required this.name,
    required this.library,
    required this.meals,
    required this.dailyTarget,
    this.description,
  });

  final int id;
  final String name;
  final String? description;
  final bool library;
  final List<DietMeal> meals;
  final Macros dailyTarget;

  factory DietPlan.fromJson(Map<String, dynamic> json) {
    final raw = (json['meals'] as List<dynamic>? ?? const []);
    return DietPlan(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      description: json['description'] as String?,
      library: json['library'] as bool? ?? false,
      meals: raw
          .map((e) => DietMeal.fromJson(e as Map<String, dynamic>))
          .toList(),
      dailyTarget:
          Macros.fromJson(json['dailyTarget'] as Map<String, dynamic>),
    );
  }
}
