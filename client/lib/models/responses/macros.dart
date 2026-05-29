/// Macro bundle — used as totals, target, and remaining on the day
/// payload and per-entry / per-item macros. Fields stay null when the
/// source food doesn't carry them (fiber, sodium), so the client renders
/// a dash rather than a misleading zero.
class Macros {
  Macros({
    this.kcal,
    this.proteinG,
    this.carbG,
    this.fatG,
    this.fiberG,
    this.sodiumMg,
  });

  final double? kcal;
  final double? proteinG;
  final double? carbG;
  final double? fatG;
  final double? fiberG;
  final double? sodiumMg;

  factory Macros.fromJson(Map<String, dynamic> json) {
    return Macros(
      kcal: (json['kcal'] as num?)?.toDouble(),
      proteinG: (json['proteinG'] as num?)?.toDouble(),
      carbG: (json['carbG'] as num?)?.toDouble(),
      fatG: (json['fatG'] as num?)?.toDouble(),
      fiberG: (json['fiberG'] as num?)?.toDouble(),
      sodiumMg: (json['sodiumMg'] as num?)?.toDouble(),
    );
  }
}
