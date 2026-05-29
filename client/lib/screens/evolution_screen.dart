import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/evaluation_summary.dart';
import '../models/responses/metric_series.dart';
import '../services/api/evaluation_api_service.dart';
import 'graph_detail_screen.dart';
import 'new_evaluation_screen.dart';

/// Evolve tab. Three sections, all loaded in parallel:
///
///   * **Hero stats** — latest weight + body-fat (when present), date
///     of the most recent evaluation.
///   * **Weight chart** (4w default) — tappable, opens
///     [GraphDetailScreen] for the full picker.
///   * **Recent evaluations list** — slim summary rows.
///
/// FAB opens [NewEvaluationScreen]. Pull-to-refresh re-fetches everything.
class EvolutionScreen extends StatefulWidget {
  const EvolutionScreen({super.key});

  @override
  State<EvolutionScreen> createState() => _EvolutionScreenState();
}

class _EvolutionScreenState extends State<EvolutionScreen> {
  bool _loading = true;
  String? _errorText;
  List<EvaluationSummary> _recent = const [];
  MetricSeries? _weight4w;

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
      final recentFuture = EvaluationApiService.listRecent(limit: 10);
      final weightFuture =
          EvaluationApiService.getSeries(metric: 'weight', range: '4w');
      final recent = await recentFuture;
      final weight = await weightFuture;
      if (!mounted) return;
      setState(() {
        _recent = recent.items;
        _weight4w = weight;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load evolution data.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load evolution data.';
        _loading = false;
      });
    }
  }

  Future<void> _newEvaluation() async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const NewEvaluationScreen()),
    );
    if (saved == true) await _load();
  }

  void _openGraph(String metric, String label, {String range = '12w'}) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => GraphDetailScreen(
          metric: metric,
          metricLabel: label,
          initialRange: range,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Evolve'), centerTitle: false),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _newEvaluation,
        icon: const Icon(Icons.add),
        label: const Text('New evaluation'),
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
    final latest = _recent.isEmpty ? null : _recent.first;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
        children: [
          _HeroStats(latest: latest),
          const SizedBox(height: 16),
          _WeightChartCard(
            series: _weight4w!,
            onTap: () => _openGraph('weight', 'Weight'),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final m in const [
                ['body_fat', 'Body fat'],
                ['waist', 'Waist'],
                ['arm_r', 'Arm (right)'],
              ])
                ActionChip(
                  avatar: const Icon(Icons.show_chart, size: 16),
                  label: Text(m[1]),
                  onPressed: () => _openGraph(m[0], m[1]),
                ),
            ],
          ),
          const SizedBox(height: 20),
          Text(
            'Recent evaluations',
            style: Theme.of(context)
                .textTheme
                .titleSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          if (_recent.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Text('No evaluations yet — tap "New evaluation" to begin.'),
            )
          else
            ..._recent.map((s) => _RecentTile(summary: s)),
        ],
      ),
    );
  }
}

// ── hero ───────────────────────────────────────────────────────────────

class _HeroStats extends StatelessWidget {
  const _HeroStats({required this.latest});

  final EvaluationSummary? latest;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Card(
      color: cs.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(Icons.monitor_weight_outlined, color: cs.onPrimaryContainer),
              const SizedBox(width: 8),
              Text(
                latest == null ? 'No data yet' : 'Latest evaluation',
                style: tt.titleMedium?.copyWith(
                  color: cs.onPrimaryContainer,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ]),
            const SizedBox(height: 12),
            if (latest == null)
              Text(
                'Log your first evaluation to start tracking weight and body fat.',
                style: tt.bodyMedium?.copyWith(color: cs.onPrimaryContainer),
              )
            else
              Row(
                children: [
                  _heroStat(
                    context,
                    label: 'Weight',
                    value: '${latest!.weightKg.toStringAsFixed(1)} kg',
                  ),
                  if (latest!.bodyFatPct != null) ...[
                    const SizedBox(width: 24),
                    _heroStat(
                      context,
                      label: 'Body fat',
                      value: '${latest!.bodyFatPct!.toStringAsFixed(1)} %',
                    ),
                  ],
                  const SizedBox(width: 24),
                  _heroStat(
                    context,
                    label: 'When',
                    value: DateFormat.MMMd().format(latest!.evaluatedAt.toLocal()),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _heroStat(BuildContext context,
      {required String label, required String value}) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value,
            style: tt.titleLarge?.copyWith(
              color: cs.onPrimaryContainer,
              fontWeight: FontWeight.w800,
            )),
        Text(label,
            style: tt.bodySmall?.copyWith(color: cs.onPrimaryContainer)),
      ],
    );
  }
}

// ── weight chart card ──────────────────────────────────────────────────

class _WeightChartCard extends StatelessWidget {
  const _WeightChartCard({required this.series, required this.onTap});

  final MetricSeries series;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final points = series.points;
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Expanded(
                  child: Text('Weight — last 4 weeks',
                      style:
                          tt.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                ),
                Icon(Icons.chevron_right, color: cs.onSurfaceVariant),
              ]),
              const SizedBox(height: 8),
              if (points.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text('No data yet — log an evaluation.',
                      style: tt.bodyMedium),
                )
              else
                SizedBox(height: 120, child: _miniLine(points, cs)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _miniLine(List<MetricPoint> points, ColorScheme cs) {
    final firstMs = points.first.at.millisecondsSinceEpoch.toDouble();
    final spots = points
        .map((p) => FlSpot(
              (p.at.millisecondsSinceEpoch.toDouble() - firstMs) /
                  (1000 * 60 * 60 * 24),
              p.value,
            ))
        .toList();
    final minY = spots.map((s) => s.y).reduce((a, b) => a < b ? a : b);
    final maxY = spots.map((s) => s.y).reduce((a, b) => a > b ? a : b);
    final pad = ((maxY - minY).abs() * 0.1).clamp(0.5, double.infinity);
    return LineChart(
      LineChartData(
        minY: minY - pad,
        maxY: maxY + pad,
        gridData: const FlGridData(show: false),
        borderData: FlBorderData(show: false),
        titlesData: const FlTitlesData(
          leftTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
        ),
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            color: cs.primary,
            barWidth: 3,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              color: cs.primary.withValues(alpha: 0.12),
            ),
          ),
        ],
      ),
    );
  }
}

// ── recent tile ────────────────────────────────────────────────────────

class _RecentTile extends StatelessWidget {
  const _RecentTile({required this.summary});

  final EvaluationSummary summary;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final date = DateFormat.MMMd().format(summary.evaluatedAt.toLocal());

    final pieces = <String>['${summary.weightKg.toStringAsFixed(1)} kg'];
    if (summary.bodyFatPct != null) {
      pieces.add('${summary.bodyFatPct!.toStringAsFixed(1)} %');
    }
    if (summary.bfMethod != null) pieces.add(summary.bfMethod!);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: cs.secondaryContainer,
            child: Icon(Icons.straighten,
                size: 18, color: cs.onSecondaryContainer),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(date,
                    style: tt.titleSmall
                        ?.copyWith(fontWeight: FontWeight.w600)),
                Text(pieces.join(' · '), style: tt.bodySmall),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
