/// Public coach card — headline, years experience, athlete count and
/// rating rollup. Mirrors backend `CoachProfileDto`. `GET
/// /api/me/coach-profile` returns a zeroed default for first read (no row
/// persisted) — the UI treats null `headline`/`yearsExperience` as
/// "not filled in yet".
class CoachProfile {
  CoachProfile({
    required this.userId,
    required this.athleteCount,
    required this.ratingCount,
    this.headline,
    this.yearsExperience,
    this.ratingAvg,
  });

  final int userId;
  final String? headline;
  final int? yearsExperience;
  final int athleteCount;
  final double? ratingAvg;
  final int ratingCount;

  bool get isEmpty =>
      (headline == null || headline!.trim().isEmpty) && yearsExperience == null;

  factory CoachProfile.fromJson(Map<String, dynamic> json) {
    return CoachProfile(
      userId: (json['userId'] as num).toInt(),
      headline: json['headline'] as String?,
      yearsExperience: (json['yearsExperience'] as num?)?.toInt(),
      athleteCount: (json['athleteCount'] as num?)?.toInt() ?? 0,
      ratingAvg: (json['ratingAvg'] as num?)?.toDouble(),
      ratingCount: (json['ratingCount'] as num?)?.toInt() ?? 0,
    );
  }
}
