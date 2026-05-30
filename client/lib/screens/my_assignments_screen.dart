import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/assignment.dart';
import '../services/api/coach_api_service.dart';

/// Athlete-side list of assignments across all active relationships. Filters
/// by status (default: open — scheduled / today / pending). Pull to refresh
/// after marking one done in another screen.
class MyAssignmentsScreen extends StatefulWidget {
  const MyAssignmentsScreen({super.key});

  @override
  State<MyAssignmentsScreen> createState() => _MyAssignmentsScreenState();
}

class _MyAssignmentsScreenState extends State<MyAssignmentsScreen> {
  bool _loading = true;
  String? _errorText;
  List<Assignment> _items = const [];
  String _statusFilter = 'open';
  final _busy = <int>{};

  @override
  void initState() {
    super.initState();
    _load();
  }

  String? _filterToBackend() {
    // 'open' is a virtual filter — the backend doesn't know it. We collapse
    // it to three separate calls would be wasteful; instead we don't pass a
    // status and filter on the client.
    if (_statusFilter == 'open' || _statusFilter == 'all') return null;
    return _statusFilter;
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final page = await CoachApiService.myAssignments(
        status: _filterToBackend(),
        limit: 100,
      );
      var items = page.items;
      if (_statusFilter == 'open') {
        items = items
            .where((a) => a.status == 'scheduled' ||
                a.status == 'today' ||
                a.status == 'pending')
            .toList(growable: false);
      }
      if (!mounted) return;
      setState(() {
        _items = items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load assignments.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load assignments.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My assignments')),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'open', label: Text('Open')),
                  ButtonSegment(value: 'done', label: Text('Done')),
                  ButtonSegment(value: 'all', label: Text('All')),
                ],
                selected: {_statusFilter},
                onSelectionChanged: (s) {
                  setState(() => _statusFilter = s.first);
                  _load();
                },
              ),
            ),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
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
    if (_items.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          children: const [
            SizedBox(height: 120),
            Center(child: Text('Nothing here.')),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        itemCount: _items.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, i) {
          final a = _items[i];
          return Card(
            child: ListTile(
              leading: _TypeIcon(type: a.type),
              title: Text(_titleFor(a)),
              subtitle: Text(_subtitleFor(a)),
              trailing: _statusFilter == 'done'
                  ? _StatusBadge(status: a.status)
                  : _busy.contains(a.id)
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2))
                      : IconButton(
                          tooltip: 'Mark done',
                          icon: const Icon(Icons.check_circle_outline),
                          onPressed: () => _markDone(a),
                        ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _markDone(Assignment a) async {
    setState(() => _busy.add(a.id));
    try {
      await CoachApiService.patchAssignment(id: a.id, status: 'done');
      if (!mounted) return;
      setState(() {
        _items = [
          for (final x in _items)
            if (x.id != a.id) x,
        ];
        _busy.remove(a.id);
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() => _busy.remove(a.id));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t update assignment.')),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() => _busy.remove(a.id));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Couldn\'t update assignment.')),
      );
    }
  }

  String _titleFor(Assignment a) {
    final base = switch (a.type) {
      'workout' => 'Workout',
      'diet' => 'Diet',
      'eval' => 'Evaluation',
      _ => a.type,
    };
    if (a.refType != null && a.refId != null) return '$base #${a.refId}';
    return base;
  }

  String _subtitleFor(Assignment a) {
    final parts = <String>[];
    if (a.scheduledFor != null) {
      final d = a.scheduledFor!;
      parts.add(
          '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}');
    }
    if ((a.notes ?? '').trim().isNotEmpty) parts.add(a.notes!.trim());
    return parts.isEmpty ? a.status : parts.join(' · ');
  }
}

class _TypeIcon extends StatelessWidget {
  const _TypeIcon({required this.type});
  final String type;

  @override
  Widget build(BuildContext context) {
    final icon = switch (type) {
      'workout' => Icons.fitness_center,
      'diet' => Icons.restaurant_outlined,
      'eval' => Icons.analytics_outlined,
      _ => Icons.assignment_outlined,
    };
    return CircleAvatar(child: Icon(icon, size: 20));
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});
  final String status;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = switch (status) {
      'done' => cs.primaryContainer,
      'skipped' => cs.errorContainer,
      _ => cs.secondaryContainer,
    };
    return Chip(
      label: Text(status),
      backgroundColor: color,
      side: BorderSide.none,
      visualDensity: VisualDensity.compact,
    );
  }
}
