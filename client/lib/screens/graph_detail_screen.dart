import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/metric_series.dart';
import '../services/api/evaluation_api_service.dart';

/// Full chart view for one metric. Range picker chips along the top
/// (4w / 12w / 6m / 1y); below the chart we list every data point so
/// the user can scan exact values without hovering. Reloads on range
/// change.
class GraphDetailScreen extends StatefulWidget {
  const GraphDetailScreen({
    super.key,
    required this.metric,
    required this.metricLabel,
    this.initialRange = '12w',
  });

  final String metric;
  final String metricLabel;
  final String initialRange;

  @override
  State<GraphDetailScreen> createState() => _GraphDetailScreenState();
}

class _GraphDetailScreenState extends State<GraphDetailScreen> {
  late String _range = widget.initialRange;
  MetricSeries? _series;
  bool _loading = true;
  String? _errorText;

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
      final series = await EvaluationApiService.getSeries(
        metric: widget.metric,
        range: _range,
      );
      if (!mounted) return;
      setState(() {
        _series = series;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load chart.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load chart.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.metricLabel)),
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
    final series = _series!;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: '4w', label: Text('4w')),
              ButtonSegment(value: '12w', label: Text('12w')),
              ButtonSegment(value: '6m', label: Text('6m')),
              ButtonSegment(value: '1y', label: Text('1y')),
            ],
            selected: {_range},
            onSelectionChanged: (s) {
              setState(() => _range = s.first);
              _load();
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: SizedBox(
            height: 220,
            child: series.points.isEmpty
                ? Center(
                    child: Text('No data in this range',
                        style: Theme.of(context).textTheme.bodyMedium),
                  )
                : _LineChartCard(series: series),
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: series.points.isEmpty
              ? const SizedBox.shrink()
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  itemCount: series.points.length,
                  separatorBuilder: (_, _) => const Divider(height: 12),
                  itemBuilder: (_, i) {
                    // Newest first in the list — chart is oldest-first; we
                    // reverse only for the list so users see most recent
                    // entries at the top.
                    final p = series.points[series.points.length - 1 - i];
                    return Row(
                      children: [
                        Expanded(
                          child: Text(
                            DateFormat.yMMMd().format(p.at.toLocal()),
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                        Text(
                          '${_trimZero(p.value)} ${series.unit}',
                          style: Theme.of(context)
                              .textTheme
                              .titleSmall
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                      ],
                    );
                  },
                ),
        ),
      ],
    );
  }

  static String _trimZero(double v) {
    if (v == v.roundToDouble()) return v.toStringAsFixed(0);
    return v.toStringAsFixed(2);
  }
}

class _LineChartCard extends StatelessWidget {
  const _LineChartCard({required this.series});

  final MetricSeries series;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    // Convert points to chart spots (x = milliseconds since first sample
    // so the axis is monotonic in time but small enough for fl_chart).
    final firstMs = series.points.first.at.millisecondsSinceEpoch.toDouble();
    final spots = series.points
        .map((p) => FlSpot(
              (p.at.millisecondsSinceEpoch.toDouble() - firstMs) /
                  (1000 * 60 * 60 * 24), // days since first
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
            dotData: const FlDotData(show: true),
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
