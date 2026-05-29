/// One measurement on an Evaluation — circumference (cm) or skinfold (mm)
/// at a named point. Mirrors backend `EvaluationMeasurementDto`. Free-form
/// `pointId` keeps new points cheap.
class EvaluationMeasurement {
  EvaluationMeasurement({
    required this.pointId,
    required this.kind,
    required this.unit,
    required this.value,
  });

  final String pointId;
  final String kind; // 'circumference' | 'skinfold'
  final String unit; // 'cm' | 'mm'
  final double value;

  factory EvaluationMeasurement.fromJson(Map<String, dynamic> json) {
    return EvaluationMeasurement(
      pointId: json['pointId'] as String,
      kind: json['kind'] as String,
      unit: json['unit'] as String,
      value: (json['value'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {
        'pointId': pointId,
        'kind': kind,
        'unit': unit,
        'value': value,
      };
}
