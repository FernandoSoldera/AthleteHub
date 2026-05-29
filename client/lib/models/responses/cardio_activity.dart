/// A logged cardio session (run / walk / cycle). Mirrors backend
/// `CardioActivityDto`. Optional fields stay null when the source can't
/// supply them — a manual log won't have HR or power, a watch fills HR
/// but not pace, etc.
class CardioActivity {
  CardioActivity({
    required this.id,
    required this.type,
    required this.distanceM,
    required this.durationSeconds,
    required this.startedAt,
    required this.source,
    this.avgPaceSPerKm,
    this.avgPowerW,
    this.avgHr,
    this.maxHr,
    this.elevationGainM,
    this.kcal,
    this.notes,
  });

  final int id;
  final String type;
  final double distanceM;
  final int durationSeconds;
  final double? avgPaceSPerKm;
  final double? avgPowerW;
  final int? avgHr;
  final int? maxHr;
  final double? elevationGainM;
  final int? kcal;
  final String? notes;
  final DateTime startedAt;
  final String source;

  double get distanceKm => distanceM / 1000;

  factory CardioActivity.fromJson(Map<String, dynamic> json) {
    return CardioActivity(
      id: (json['id'] as num).toInt(),
      type: json['type'] as String,
      distanceM: (json['distanceM'] as num?)?.toDouble() ?? 0,
      durationSeconds: (json['durationSeconds'] as num?)?.toInt() ?? 0,
      avgPaceSPerKm: (json['avgPaceSPerKm'] as num?)?.toDouble(),
      avgPowerW: (json['avgPowerW'] as num?)?.toDouble(),
      avgHr: (json['avgHr'] as num?)?.toInt(),
      maxHr: (json['maxHr'] as num?)?.toInt(),
      elevationGainM: (json['elevationGainM'] as num?)?.toDouble(),
      kcal: (json['kcal'] as num?)?.toInt(),
      notes: json['notes'] as String?,
      startedAt: DateTime.parse(json['startedAt'] as String),
      source: json['source'] as String? ?? 'self',
    );
  }
}
