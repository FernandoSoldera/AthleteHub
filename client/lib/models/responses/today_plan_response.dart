import 'workout_template.dart';

/// `GET /api/training/today` payload — both fields independently nullable
/// because the four combinations are all real UX states:
///   * `template == null && activeSessionId == null` → rest day
///   * `template != null && activeSessionId == null` → ready to start
///   * `template == null && activeSessionId != null` → yesterday's session
///     still open
///   * `template != null && activeSessionId != null` → today's plan +
///     in-progress session (offer Resume, not Start).
class TodayPlanResponse {
  TodayPlanResponse({this.template, this.activeSessionId});

  final WorkoutTemplate? template;
  final int? activeSessionId;

  bool get hasPlan => template != null;
  bool get hasActiveSession => activeSessionId != null;

  factory TodayPlanResponse.fromJson(Map<String, dynamic> json) {
    final t = json['template'];
    return TodayPlanResponse(
      template: t == null ? null : WorkoutTemplate.fromJson(t as Map<String, dynamic>),
      activeSessionId: (json['activeSessionId'] as num?)?.toInt(),
    );
  }
}
