import 'macros.dart';

/// One diary entry as returned by the day endpoint or the create endpoint.
class DiaryEntry {
  DiaryEntry({
    required this.id,
    required this.foodId,
    required this.foodName,
    required this.eatenAt,
    required this.amount,
    required this.unit,
    required this.source,
    required this.macros,
    this.mealLabel,
  });

  final int id;
  final int foodId;
  final String foodName;
  final DateTime eatenAt;
  final double amount;
  final String unit;
  final String? mealLabel;
  final String source;
  final Macros macros;

  factory DiaryEntry.fromJson(Map<String, dynamic> json) {
    return DiaryEntry(
      id: (json['id'] as num).toInt(),
      foodId: (json['foodId'] as num).toInt(),
      foodName: json['foodName'] as String,
      eatenAt: DateTime.parse(json['eatenAt'] as String),
      amount: (json['amount'] as num).toDouble(),
      unit: json['unit'] as String,
      mealLabel: json['mealLabel'] as String?,
      source: json['source'] as String? ?? 'self',
      macros: Macros.fromJson(json['macros'] as Map<String, dynamic>),
    );
  }
}
