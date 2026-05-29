/// Slim evaluation view for the recent-evaluations list — no measurements.
/// Mirrors backend `EvaluationSummaryDto`.
class EvaluationSummary {
  EvaluationSummary({
    required this.id,
    required this.evaluatedAt,
    required this.weightKg,
    required this.source,
    this.bodyFatPct,
    this.bfMethod,
  });

  final int id;
  final DateTime evaluatedAt;
  final double weightKg;
  final double? bodyFatPct;
  final String? bfMethod;
  final String source;

  factory EvaluationSummary.fromJson(Map<String, dynamic> json) {
    return EvaluationSummary(
      id: (json['id'] as num).toInt(),
      evaluatedAt: DateTime.parse(json['evaluatedAt'] as String),
      weightKg: (json['weightKg'] as num).toDouble(),
      bodyFatPct: (json['bodyFatPct'] as num?)?.toDouble(),
      bfMethod: json['bfMethod'] as String?,
      source: json['source'] as String? ?? 'self',
    );
  }
}
