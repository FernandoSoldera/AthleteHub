import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/today_plan_response.dart';
import '../models/responses/weekly_summary.dart';
import '../models/responses/workout_session_summary.dart';
import '../services/api/training_api_service.dart';
import 'cardio_screen.dart';
import 'workout_screen.dart';

/// Train tab — today's plan as a hero card with Start / Resume / Rest day
/// CTA, weekly cardio bar chart, recent sessions list. Pull-to-refresh
/// re-fetches all three calls in parallel.
class TrainScreen extends StatefulWidget {
  const TrainScreen({super.key});

  @override
  State<TrainScreen> createState() => _TrainScreenState();
}

class _TrainScreenState extends State<TrainScreen> {
  bool _loading = true;
  String? _errorText;
  TodayPlanResponse? _today;
  WeeklySummary? _weekly;
  List<WorkoutSessionSummary> _recent = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final results = await Future.wait([
        TrainingApiService.today(),
        TrainingApiService.weeklySummary(),
        TrainingApiService.recentSessions(limit: 10),
      ]);
      if (!mounted) return;
      setState(() {
        _today = results[0] as TodayPlanResponse;
        _weekly = results[1] as WeeklySummary;
        _recent = (results[2] as dynamic).items as List<WorkoutSessionSummary>;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load training data.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load training data.';
        _loading = false;
      });
    }
  }

  Future<void> _startSession({int? templateId}) async {
    try {
      final session = await TrainingApiService.startSession(templateId: templateId);
      if (!mounted) return;
      final didFinish = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => WorkoutScreen(sessionId: session.id)),
      );
      if (didFinish == true) await _load();
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t start session.')),
      );
    }
  }

  Future<void> _resumeActive() async {
    final id = _today?.activeSessionId;
    if (id == null) return;
    final didFinish = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => WorkoutScreen(sessionId: id)),
    );
    if (didFinish == true) await _load();
  }

  Future<void> _logCardio() async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const CardioScreen()),
    );
    if (saved == true) await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Train'),
        centerTitle: false,
        actions: [
          IconButton(
            tooltip: 'Log cardio',
            icon: const Icon(Icons.directions_run),
            onPressed: _logCardio,
          ),
        ],
      ),
      body: SafeArea(child: _buildBody()),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_errorText != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_errorText!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: _load, child: const Text('Try again')),
            ],
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          _HeroCard(
            today: _today!,
            onStart: () => _startSession(templateId: _today!.template?.id),
            onResume: _resumeActive,
          ),
          const SizedBox(height: 20),
          if (_weekly != null) _WeeklyCardioCard(weekly: _weekly!),
          const SizedBox(height: 20),
          Text(
            'Recent sessions',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 8),
          if (_recent.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Text('No sessions yet — start one above.'),
            )
          else
            ..._recent.map((s) => _RecentSessionTile(summary: s)),
        ],
      ),
    );
  }
}

// ── hero ───────────────────────────────────────────────────────────────

class _HeroCard extends StatelessWidget {
  const _HeroCard({
    required this.today,
    required this.onStart,
    required this.onResume,
  });

  final TodayPlanResponse today;
  final VoidCallback onStart;
  final VoidCallback onResume;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;

    String headline;
    String subtitle;
    String ctaLabel;
    VoidCallback? cta;

    if (today.hasActiveSession) {
      headline = 'Session in progress';
      subtitle = 'Pick up where you left off.';
      ctaLabel = 'Resume';
      cta = onResume;
    } else if (today.hasPlan) {
      headline = today.template!.name;
      subtitle = today.template!.exercises.isEmpty
          ? 'Tap start to log a freestyle session.'
          : '${today.template!.exercises.length} exercises queued.';
      ctaLabel = 'Start';
      cta = onStart;
    } else {
      headline = 'Rest day';
      subtitle = 'No plan scheduled for today. Tap to log a freestyle session.';
      ctaLabel = 'Start freestyle';
      cta = onStart;
    }

    return Card(
      color: cs.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(Icons.fitness_center, color: cs.onPrimaryContainer),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  headline,
                  style: tt.titleLarge?.copyWith(
                    color: cs.onPrimaryContainer,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ]),
            const SizedBox(height: 6),
            Text(subtitle,
                style: tt.bodyMedium?.copyWith(color: cs.onPrimaryContainer)),
            if (today.hasPlan && today.template!.exercises.isNotEmpty) ...[
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final e in today.template!.exercises)
                    Chip(
                      label: Text(
                        e.scheme == null ? e.name : '${e.name} · ${e.scheme}',
                      ),
                      backgroundColor: cs.surface,
                    ),
                ],
              ),
            ],
            const SizedBox(height: 14),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(onPressed: cta, child: Text(ctaLabel)),
            ),
          ],
        ),
      ),
    );
  }
}

// ── weekly cardio chart ────────────────────────────────────────────────

class _WeeklyCardioCard extends StatelessWidget {
  const _WeeklyCardioCard({required this.weekly});

  final WeeklySummary weekly;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final delta = weekly.deltaKm;
    final deltaPositive = delta >= 0;
    final deltaLabel = '${deltaPositive ? '+' : ''}${delta.toStringAsFixed(2)} km';

    final maxKm =
        [weekly.thisWeekKm, weekly.lastWeekKm, 1.0].reduce((a, b) => a > b ? a : b);

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('This week — cardio',
                      style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: deltaPositive
                        ? Colors.green.withValues(alpha: 0.15)
                        : Colors.red.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        deltaPositive ? Icons.trending_up : Icons.trending_down,
                        size: 14,
                        color: deltaPositive ? Colors.green[700] : Colors.red[700],
                      ),
                      const SizedBox(width: 4),
                      Text(
                        deltaLabel,
                        style: TextStyle(
                          fontSize: 12,
                          color: deltaPositive ? Colors.green[700] : Colors.red[700],
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              '${weekly.thisWeekKm.toStringAsFixed(2)} km',
              style: tt.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 90,
              child: BarChart(
                BarChartData(
                  alignment: BarChartAlignment.spaceAround,
                  maxY: maxKm * 1.2,
                  borderData: FlBorderData(show: false),
                  gridData: const FlGridData(show: false),
                  titlesData: FlTitlesData(
                    leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 22,
                        getTitlesWidget: (value, _) {
                          final text = value.toInt() == 0 ? 'Last week' : 'This week';
                          return Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(text, style: tt.bodySmall),
                          );
                        },
                      ),
                    ),
                  ),
                  barGroups: [
                    BarChartGroupData(x: 0, barRods: [
                      BarChartRodData(
                        toY: weekly.lastWeekKm,
                        color: cs.secondary,
                        width: 32,
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ]),
                    BarChartGroupData(x: 1, barRods: [
                      BarChartRodData(
                        toY: weekly.thisWeekKm,
                        color: cs.primary,
                        width: 32,
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ]),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── recent sessions ────────────────────────────────────────────────────

class _RecentSessionTile extends StatelessWidget {
  const _RecentSessionTile({required this.summary});

  final WorkoutSessionSummary summary;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final date = DateFormat.MMMd().format(summary.startedAt.toLocal());

    final pieces = <String>[];
    if (summary.totalSets > 0) pieces.add('${summary.totalSets} sets');
    if (summary.totalVolumeKg > 0) {
      pieces.add('${summary.totalVolumeKg.toStringAsFixed(0)} kg');
    }
    if (summary.prCount > 0) pieces.add('${summary.prCount} PR${summary.prCount == 1 ? '' : 's'}');
    final subtitle = pieces.isEmpty ? 'In progress' : pieces.join(' · ');

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: cs.secondaryContainer,
            child: Icon(
              summary.isInProgress ? Icons.play_arrow : Icons.check,
              size: 18,
              color: cs.onSecondaryContainer,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  summary.title,
                  style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                ),
                Text(subtitle, style: tt.bodySmall),
              ],
            ),
          ),
          Text(date, style: tt.bodySmall),
        ],
      ),
    );
  }
}
