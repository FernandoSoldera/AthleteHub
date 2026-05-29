import 'meal_item.dart';

/// One named meal inside a `DietPlan` — items materialized.
class DietMeal {
  DietMeal({
    required this.id,
    required this.position,
    required this.name,
    required this.items,
    this.timeHint,
  });

  final int id;
  final int position;
  final String name;
  final String? timeHint;
  final List<MealItem> items;

  factory DietMeal.fromJson(Map<String, dynamic> json) {
    final raw = (json['items'] as List<dynamic>? ?? const []);
    return DietMeal(
      id: (json['id'] as num).toInt(),
      position: (json['position'] as num).toInt(),
      name: json['name'] as String,
      timeHint: json['timeHint'] as String?,
      items:
          raw.map((e) => MealItem.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}
