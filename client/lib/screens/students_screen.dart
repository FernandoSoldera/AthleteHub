import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/roster_entry.dart';
import '../services/api/coach_api_service.dart';
import '../widgets/avatar.dart';
import 'invite_athlete_sheet.dart';
import 'student_detail_screen.dart';

/// Coach-side roster — list of athletes coached by the caller. Filter by
/// active / pending status. Tap a row to drill into the student detail.
class StudentsScreen extends StatefulWidget {
  const StudentsScreen({super.key});

  @override
  State<StudentsScreen> createState() => _StudentsScreenState();
}

class _StudentsScreenState extends State<StudentsScreen> {
  bool _loading = true;
  String? _errorText;
  List<RosterEntry> _items = const [];
  String _statusFilter = 'active';

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
      final page = await CoachApiService.roster(
        status: _statusFilter == 'all' ? null : _statusFilter,
        limit: 100,
      );
      if (!mounted) return;
      setState(() {
        _items = page.items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load roster.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load roster.';
        _loading = false;
      });
    }
  }

  Future<void> _openInviteSheet() async {
    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const InviteAthleteSheet(),
    );
    if (result == true) _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My athletes')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openInviteSheet,
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('Invite'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'active', label: Text('Active')),
                  ButtonSegment(value: 'pending', label: Text('Pending')),
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
            Center(child: Text('No athletes here yet.')),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
        itemCount: _items.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, i) {
          final r = _items[i];
          return Card(
            child: ListTile(
              leading: Avatar(
                  fullName: r.athlete.fullName,
                  hue: r.athlete.avatarHue,
                  size: 44),
              title: Text(r.athlete.fullName),
              subtitle: Text(
                [
                  '@${r.athlete.handle}',
                  if (r.goal != null) r.goal!,
                  if (r.adherencePct != null) '${r.adherencePct}% adherence',
                ].join(' · '),
              ),
              trailing: _RosterTrailing(entry: r),
              onTap: () async {
                await Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => StudentDetailScreen(athleteId: r.athlete.id),
                  ),
                );
                if (!mounted) return;
                _load();
              },
            ),
          );
        },
      ),
    );
  }
}

class _RosterTrailing extends StatelessWidget {
  const _RosterTrailing({required this.entry});
  final RosterEntry entry;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final flag = entry.flag;
    if (flag == null || flag.isEmpty || flag == 'ok') {
      return const Icon(Icons.chevron_right);
    }
    final color = switch (flag) {
      'inactive' || 'at_risk' => cs.errorContainer,
      _ => cs.tertiaryContainer,
    };
    return Chip(
      label: Text(flag),
      backgroundColor: color,
      side: BorderSide.none,
      visualDensity: VisualDensity.compact,
    );
  }
}
