import 'food.dart';

/// A favorited food bookmark for Quick-Add. Mirrors backend `FavoriteDto`.
class Favorite {
  Favorite({
    required this.id,
    required this.createdAt,
    this.food,
  });

  final int id;
  final DateTime createdAt;
  final Food? food;

  factory Favorite.fromJson(Map<String, dynamic> json) {
    return Favorite(
      id: (json['id'] as num).toInt(),
      createdAt: DateTime.parse(json['createdAt'] as String),
      food: json['food'] == null
          ? null
          : Food.fromJson(json['food'] as Map<String, dynamic>),
    );
  }
}
