import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/assignment.dart';
import '../models/responses/cursor_page.dart';
import '../models/responses/student_detail.dart';
import '../services/api/coach_api_service.dart';
import '../services/api/messaging_api_service.dart';
import '../widgets/avatar.dart';
import 'assign_sheet.dart';
import 'chat_screen.dart';

/// Coach's deep-dive view for one athlete — header card, recent rollups
/// (latest eval, weekly cardio, recent workouts), and an assignments list
/// with a "+ Assign" button.
class StudentDetailScreen extends StatefulWidget {
  const StudentDetailScreen({super.key, required this.athleteId});

  final int athleteId;

  @override
  State<StudentDetailScreen> createState() => _StudentDetailScreenState();
}

class _StudentDetailScreenState extends State<StudentDetailScreen> {
  bool _loading = true;
  String? _errorText;
  StudentDetail? _detail;
  List<Assignment> _assignments = const [];

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
      final detailFuture = CoachApiService.studentDetail(widget.athleteId);
      final assignsFuture = CoachApiService.assignmentsForAthlete(
        athleteId: widget.athleteId,
        limit: 20,
      );
      final detail = await detailFuture;
      final CursorPage<Assignment> assigns = await assignsFuture;
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _assignments = assigns.items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load athlete.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load athlete.';
        _loading = false;
      });
    }
  }

  Future<void> _openAssignSheet() async {
    final detail = _detail;
    if (detail == null) return;
    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => AssignSheet(athleteId: detail.athlete.id),
    );
    if (result == true) _load();
  }

  Future<void> _openChat() async {
    final detail = _detail;
    if (detail == null) return;
    try {
      final convo =
          await MessagingApiService.openForRelationship(detail.relationshipId);
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => ChatScreen(conversation: convo),
      ));
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t open chat.')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Couldn\'t open chat.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_detail?.athlete.fullName ?? 'Athlete'),
        actions: [
          if (_detail != null)
            IconButton(
              tooltip: 'Message',
              icon: const Icon(Icons.chat_bubble_outline),
              onPressed: _openChat,
            ),
        ],
      ),
      floatingActionButton: _detail == null
          ? null
          : FloatingActionButton.extended(
              onPressed: _openAssignSheet,
              icon: const Icon(Icons.add_task),
              label: const Text('Assign'),
            ),
      body: SafeArea(child: _buildBody()),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_errorText != null || _detail == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_errorText ?? 'Couldn\'t load athlete.',
                  textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: _load, child: const Text('Try again')),
            ],
          ),
        ),
      );
    }

    final d = _detail!;
    final tt = Theme.of(context).textTheme;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        children: [
          Row(
            children: [
              Avatar(
                  fullName: d.athlete.fullName,
                  hue: d.athlete.avatarHue,
                  size: 56),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(d.athlete.fullName,
                        style: tt.titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700)),
                    Text('@${d.athlete.handle}', style: tt.bodyMedium),
                  ],
                ),
              ),
            ],
          ),
          if (d.goal != null && d.goal!.trim().isNotEmpty) ...[
            const SizedBox(height: 12),
            Text('Goal: ${d.goal!}', style: tt.bodyMedium),
          ],
          const SizedBox(height: 20),
          _SectionTitle(text: 'Latest evaluation'),
          if (d.latestEvaluation == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('No evaluation logged yet.'),
            )
          else
            Card(
              child: ListTile(
                leading: const Icon(Icons.analytics_outlined),
                title: Text('${d.latestEvaluation!.weightKg.toStringAsFixed(1)} kg'),
                subtitle: Text(
                  d.latestEvaluation!.bodyFatPct == null
                      ? 'Body fat: —'
                      : 'Body fat: ${d.latestEvaluation!.bodyFatPct!.toStringAsFixed(1)}%',
                ),
                trailing: Text(_fmtDate(d.latestEvaluation!.evaluatedAt)),
              ),
            ),
          const SizedBox(height: 16),
          _SectionTitle(text: 'Weekly cardio'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _Stat(label: 'This week',
                      value: '${d.weeklyCardio.thisWeekKm.toStringAsFixed(1)} km'),
                  _Stat(label: 'Last week',
                      value: '${d.weeklyCardio.lastWeekKm.toStringAsFixed(1)} km'),
                  _Stat(label: 'Δ',
                      value: '${d.weeklyCardio.deltaKm >= 0 ? '+' : ''}${d.weeklyCardio.deltaKm.toStringAsFixed(1)} km'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _SectionTitle(text: 'Recent sessions'),
          if (d.recentSessions.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('No sessions logged yet.'),
            )
          else
            ...d.recentSessions.map((s) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.fitness_center),
                    title: Text(s.title),
                    subtitle: Text(
                        '${_fmtDate(s.startedAt)} · ${s.totalSets} sets · ${s.totalVolumeKg.toStringAsFixed(0)} kg'),
                    trailing: s.prCount > 0
                        ? Chip(
                            label: Text('${s.prCount} PR'),
                            visualDensity: VisualDensity.compact,
                            side: BorderSide.none,
                            backgroundColor:
                                Theme.of(context).colorScheme.primaryContainer,
                          )
                        : null,
                  ),
                )),
          const SizedBox(height: 16),
          _SectionTitle(text: 'Assignments'),
          if (_assignments.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('No assignments yet — tap "Assign" to add one.'),
            )
          else
            ..._assignments.map((a) => Card(
                  child: ListTile(
                    leading: Icon(_iconForType(a.type)),
                    title: Text(_titleForAssignment(a)),
                    subtitle: Text(_subtitleForAssignment(a)),
                    trailing: Chip(
                      label: Text(a.status),
                      visualDensity: VisualDensity.compact,
                      side: BorderSide.none,
                    ),
                  ),
                )),
        ],
      ),
    );
  }

  String _fmtDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  IconData _iconForType(String type) => switch (type) {
        'workout' => Icons.fitness_center,
        'diet' => Icons.restaurant_outlined,
        'eval' => Icons.analytics_outlined,
        _ => Icons.assignment_outlined,
      };

  String _titleForAssignment(Assignment a) {
    final base = switch (a.type) {
      'workout' => 'Workout',
      'diet' => 'Diet',
      'eval' => 'Evaluation',
      _ => a.type,
    };
    if (a.refType != null && a.refId != null) return '$base #${a.refId}';
    return base;
  }

  String _subtitleForAssignment(Assignment a) {
    final parts = <String>[];
    if (a.scheduledFor != null) parts.add(_fmtDate(a.scheduledFor!));
    if ((a.notes ?? '').trim().isNotEmpty) parts.add(a.notes!.trim());
    return parts.isEmpty ? '—' : parts.join(' · ');
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          text.toUpperCase(),
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                letterSpacing: 1.2,
                fontWeight: FontWeight.w700,
              ),
        ),
      );
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Column(
      children: [
        Text(value,
            style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 2),
        Text(label, style: tt.bodySmall),
      ],
    );
  }
}
