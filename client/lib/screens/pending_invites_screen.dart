import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/coach_invite.dart';
import '../services/api/coach_api_service.dart';
import '../widgets/avatar.dart';

/// Athlete-side inbox of coach invites. Pending rows offer accept / decline;
/// already-resolved ones (accepted / declined) appear muted at the bottom so
/// the athlete can see their history.
class PendingInvitesScreen extends StatefulWidget {
  const PendingInvitesScreen({super.key});

  @override
  State<PendingInvitesScreen> createState() => _PendingInvitesScreenState();
}

class _PendingInvitesScreenState extends State<PendingInvitesScreen> {
  bool _loading = true;
  String? _errorText;
  List<CoachInvite> _invites = const [];
  final _busy = <int>{};

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
      final invites = await CoachApiService.myInvites();
      if (!mounted) return;
      setState(() {
        _invites = invites;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load invites.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load invites.';
        _loading = false;
      });
    }
  }

  Future<void> _act(CoachInvite invite, bool accept) async {
    setState(() => _busy.add(invite.id));
    try {
      final updated = accept
          ? await CoachApiService.acceptInvite(invite.id)
          : await CoachApiService.declineInvite(invite.id);
      if (!mounted) return;
      setState(() {
        _invites = [
          for (final i in _invites)
            if (i.id == invite.id) updated else i,
        ];
        _busy.remove(invite.id);
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() => _busy.remove(invite.id));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t update invite.')),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() => _busy.remove(invite.id));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Couldn\'t update invite.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Coach invites')),
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
    if (_invites.isEmpty) {
      return const Center(
        child: Text('No coach invites yet.', style: TextStyle(fontSize: 16)),
      );
    }

    final pending = _invites.where((i) => i.status == 'pending').toList();
    final resolved = _invites.where((i) => i.status != 'pending').toList();

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          if (pending.isNotEmpty) ...[
            const _SectionLabel('Pending'),
            for (final i in pending) _InviteTile(
              invite: i,
              busy: _busy.contains(i.id),
              onAccept: () => _act(i, true),
              onDecline: () => _act(i, false),
            ),
          ],
          if (resolved.isNotEmpty) ...[
            const SizedBox(height: 16),
            const _SectionLabel('History'),
            for (final i in resolved) _InviteTile(invite: i),
          ],
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          text.toUpperCase(),
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                letterSpacing: 1.2,
                fontWeight: FontWeight.w700,
              ),
        ),
      );
}

class _InviteTile extends StatelessWidget {
  const _InviteTile({
    required this.invite,
    this.busy = false,
    this.onAccept,
    this.onDecline,
  });

  final CoachInvite invite;
  final bool busy;
  final VoidCallback? onAccept;
  final VoidCallback? onDecline;

  @override
  Widget build(BuildContext context) {
    final coach = invite.coach;
    final tt = Theme.of(context).textTheme;
    final muted = invite.status != 'pending';
    return Opacity(
      opacity: muted ? 0.65 : 1.0,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  if (coach != null)
                    Avatar(fullName: coach.fullName, hue: coach.avatarHue, size: 44),
                  if (coach != null) const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(coach?.fullName ?? 'Unknown coach',
                            style: tt.titleSmall
                                ?.copyWith(fontWeight: FontWeight.w700)),
                        if (coach != null)
                          Text('@${coach.handle}', style: tt.bodySmall),
                      ],
                    ),
                  ),
                  _StatusChip(status: invite.status),
                ],
              ),
              if (!muted) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: busy ? null : onDecline,
                        child: const Text('Decline'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: busy ? null : onAccept,
                        child: busy
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('Accept'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});
  final String status;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = switch (status) {
      'active' || 'accepted' => cs.primaryContainer,
      'declined' || 'ended' => cs.errorContainer,
      _ => cs.secondaryContainer,
    };
    return Chip(
      label: Text(status),
      backgroundColor: color,
      visualDensity: VisualDensity.compact,
      side: BorderSide.none,
    );
  }
}
