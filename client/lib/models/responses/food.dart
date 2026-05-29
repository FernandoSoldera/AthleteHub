/// A food the user can log. Mirrors backend `FoodDto`. `custom = true`
/// marks the caller's own entries so the client can show a "Custom"
/// badge without re-deriving from `createdBy` (which isn't exposed).
class Food {
  Food({
    required this.id,
    required this.name,
    required this.custom,
    required this.servingSizeG,
    required this.kcal,
    required this.proteinG,
    required this.carbG,
    required this.fatG,
    this.brand,
    this.fiberG,
    this.sodiumMg,
  });

  final int id;
  final String name;
  final String? brand;
  final bool custom;
  final double servingSizeG;
  final double kcal;
  final double proteinG;
  final double carbG;
  final double fatG;
  final double? fiberG;
  final double? sodiumMg;

  factory Food.fromJson(Map<String, dynamic> json) {
    return Food(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      brand: json['brand'] as String?,
      custom: json['custom'] as bool? ?? false,
      servingSizeG: (json['servingSizeG'] as num).toDouble(),
      kcal: (json['kcal'] as num).toDouble(),
      proteinG: (json['proteinG'] as num).toDouble(),
      carbG: (json['carbG'] as num).toDouble(),
      fatG: (json['fatG'] as num).toDouble(),
      fiberG: (json['fiberG'] as num?)?.toDouble(),
      sodiumMg: (json['sodiumMg'] as num?)?.toDouble(),
    );
  }
}
