import 'food.dart';
import 'macros.dart';

/// One item inside a `DietMeal` — food + amount/unit + scaled macros.
class MealItem {
  MealItem({
    required this.id,
    required this.position,
    required this.amount,
    required this.unit,
    required this.food,
    required this.macros,
  });

  final int id;
  final int position;
  final double amount;
  final String unit;
  final Food? food;
  final Macros macros;

  factory MealItem.fromJson(Map<String, dynamic> json) {
    return MealItem(
      id: (json['id'] as num).toInt(),
      position: (json['position'] as num).toInt(),
      amount: (json['amount'] as num).toDouble(),
      unit: json['unit'] as String,
      food: json['food'] == null
          ? null
          : Food.fromJson(json['food'] as Map<String, dynamic>),
      macros: Macros.fromJson(json['macros'] as Map<String, dynamic>),
    );
  }
}
