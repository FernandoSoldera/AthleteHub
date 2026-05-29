/// One sample in a metric series — the chart x-axis comes from `at`,
/// y-axis from `value`. Mirrors backend `MetricPoint`.
class MetricPoint {
  MetricPoint({required this.at, required this.value});

  final DateTime at;
  final double value;

  factory MetricPoint.fromJson(Map<String, dynamic> json) {
    return MetricPoint(
      at: DateTime.parse(json['at'] as String),
      value: (json['value'] as num).toDouble(),
    );
  }
}

/// Time-series payload for a Body chart. Mirrors backend `MetricSeriesDto`.
/// Points come ordered oldest → newest so the chart renders straight in.
/// `unit` is server-derived (`kg`, `%`, `cm`, `mm`); empty when the user
/// has no data for that point yet.
class MetricSeries {
  MetricSeries({
    required this.metric,
    required this.range,
    required this.unit,
    required this.points,
  });

  final String metric;
  final String range;
  final String unit;
  final List<MetricPoint> points;

  factory MetricSeries.fromJson(Map<String, dynamic> json) {
    final raw = (json['points'] as List<dynamic>? ?? const []);
    return MetricSeries(
      metric: json['metric'] as String,
      range: json['range'] as String,
      unit: json['unit'] as String? ?? '',
      points: raw
          .map((e) => MetricPoint.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
