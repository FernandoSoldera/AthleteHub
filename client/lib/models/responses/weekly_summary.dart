/// This-week vs last-week cardio totals (km). Mirrors backend
/// `WeeklySummaryDto`. `deltaKm` is signed — negative when this week is
/// lower, which the chart renders as a downward delta chip.
class WeeklySummary {
  WeeklySummary({
    required this.thisWeekKm,
    required this.lastWeekKm,
    required this.deltaKm,
  });

  final double thisWeekKm;
  final double lastWeekKm;
  final double deltaKm;

  factory WeeklySummary.fromJson(Map<String, dynamic> json) {
    return WeeklySummary(
      thisWeekKm: (json['thisWeekKm'] as num?)?.toDouble() ?? 0,
      lastWeekKm: (json['lastWeekKm'] as num?)?.toDouble() ?? 0,
      deltaKm: (json['deltaKm'] as num?)?.toDouble() ?? 0,
    );
  }
}
