import 'evaluation_measurement.dart';

/// Full evaluation view — row + measurements. Mirrors backend `EvaluationDto`.
/// The body-fat pair (`bodyFatPct`, `bfMethod`) stays null for a weight-only
/// check-in; the schema XOR keeps them agreed on presence.
class Evaluation {
  Evaluation({
    required this.id,
    required this.evaluatedAt,
    required this.weightKg,
    required this.source,
    required this.measurements,
    this.bodyFatPct,
    this.bfMethod,
    this.notes,
  });

  final int id;
  final DateTime evaluatedAt;
  final double weightKg;
  final double? bodyFatPct;
  final String? bfMethod;
  final String? notes;
  final String source;
  final List<EvaluationMeasurement> measurements;

  factory Evaluation.fromJson(Map<String, dynamic> json) {
    final raw = (json['measurements'] as List<dynamic>? ?? const []);
    return Evaluation(
      id: (json['id'] as num).toInt(),
      evaluatedAt: DateTime.parse(json['evaluatedAt'] as String),
      weightKg: (json['weightKg'] as num).toDouble(),
      bodyFatPct: (json['bodyFatPct'] as num?)?.toDouble(),
      bfMethod: json['bfMethod'] as String?,
      notes: json['notes'] as String?,
      source: json['source'] as String? ?? 'self',
      measurements: raw
          .map((e) => EvaluationMeasurement.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
