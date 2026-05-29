import 'diary_entry.dart';
import 'macros.dart';

/// `GET /api/diet/day?date=…` payload.
///
/// * `target` and `remaining` are both null when no active diet plan is
///   set — same nullable-axis rule as the body-fat XOR (AH-041) and the
///   today-plan response (AH-032). The four combinations are all real
///   UX states (no diary + no plan, diary + no plan, no diary + plan,
///   diary + plan).
/// * `remaining` can go negative when the user is over target.
class DayResponse {
  DayResponse({
    required this.date,
    required this.entries,
    required this.totals,
    this.target,
    this.remaining,
  });

  final DateTime date;
  final List<DiaryEntry> entries;
  final Macros totals;
  final Macros? target;
  final Macros? remaining;

  bool get hasTarget => target != null;

  factory DayResponse.fromJson(Map<String, dynamic> json) {
    final raw = (json['entries'] as List<dynamic>? ?? const []);
    return DayResponse(
      date: DateTime.parse(json['date'] as String),
      entries:
          raw.map((e) => DiaryEntry.fromJson(e as Map<String, dynamic>)).toList(),
      totals: Macros.fromJson(json['totals'] as Map<String, dynamic>),
      target: json['target'] == null
          ? null
          : Macros.fromJson(json['target'] as Map<String, dynamic>),
      remaining: json['remaining'] == null
          ? null
          : Macros.fromJson(json['remaining'] as Map<String, dynamic>),
    );
  }
}
