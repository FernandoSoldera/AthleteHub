import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/feed_item.dart';
import 'avatar.dart';

/// One feed card. Reads the per-type snapshot from `item.post.payload`
/// — payload shapes are documented in the backend's `PostService` and
/// vary by `post.type`:
///
///   * **workout** — `title, totalVolumeKg, totalSets, prCount, durationSeconds?`
///   * **run / cycle** — `type, distanceM, durationSeconds, avgPaceSPerKm?, avgHr?, kcal?`
///   * **evolution** — `weightKg, bodyFatPct?, bfMethod?, evaluatedAt`
///   * **manual** — payload is null; renders `title` + `note` instead
///
/// Unknown types fall through to the manual-style render. Designed so
/// adding a new post type is one new `case` branch.
class FeedCard extends StatelessWidget {
  const FeedCard({
    super.key,
    required this.item,
    required this.onLikeTap,
    required this.onCommentTap,
    required this.onAuthorTap,
    this.onLongPress,
  });

  final FeedItem item;
  final VoidCallback onLikeTap;
  final VoidCallback onCommentTap;
  final VoidCallback onAuthorTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final author = item.author;
    final post = item.post;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: InkWell(
        onTap: onCommentTap,
        onLongPress: onLongPress,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  GestureDetector(
                    onTap: onAuthorTap,
                    child: Avatar(
                      fullName: author?.fullName ?? '?',
                      hue: author?.avatarHue,
                      size: 36,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: GestureDetector(
                      onTap: onAuthorTap,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            author?.fullName ?? 'Unknown',
                            style: tt.titleSmall
                                ?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          Text(
                            '@${author?.handle ?? '?'} · '
                            '${DateFormat.MMMd().add_jm().format(post.createdAt.toLocal())}',
                            style: tt.bodySmall,
                          ),
                        ],
                      ),
                    ),
                  ),
                  Icon(_iconForType(post.type),
                      size: 18, color: cs.onSurfaceVariant),
                ],
              ),
              const SizedBox(height: 10),
              _buildBody(context),
              const SizedBox(height: 6),
              Row(
                children: [
                  _action(
                    context,
                    icon: item.iLiked ? Icons.favorite : Icons.favorite_border,
                    color: item.iLiked ? cs.error : cs.onSurfaceVariant,
                    label: '${post.likeCount}',
                    onTap: onLikeTap,
                  ),
                  const SizedBox(width: 12),
                  _action(
                    context,
                    icon: Icons.chat_bubble_outline,
                    color: cs.onSurfaceVariant,
                    label: '${post.commentCount}',
                    onTap: onCommentTap,
                  ),
                  const Spacer(),
                  _visibilityBadge(context, post.visibility),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── per-type body ────────────────────────────────────────────────────

  Widget _buildBody(BuildContext context) {
    final post = item.post;
    final payload = post.payload ?? const {};
    switch (post.type) {
      case 'workout':
        return _workoutBody(context, payload);
      case 'run':
      case 'cycle':
        return _cardioBody(context, payload);
      case 'evolution':
        return _evolutionBody(context, payload);
      case 'manual':
      default:
        return _manualBody(context, post.title, post.note);
    }
  }

  Widget _workoutBody(BuildContext context, Map<String, dynamic> payload) {
    final tt = Theme.of(context).textTheme;
    final title = payload['title']?.toString() ?? item.post.title ?? 'Workout';
    final vol = (payload['totalVolumeKg'] as num?)?.toDouble();
    final sets = (payload['totalSets'] as num?)?.toInt();
    final prs = (payload['prCount'] as num?)?.toInt() ?? 0;
    final dur = (payload['durationSeconds'] as num?)?.toInt();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        Wrap(spacing: 16, runSpacing: 4, children: [
          if (vol != null) _stat('Volume', '${vol.toStringAsFixed(0)} kg'),
          if (sets != null) _stat('Sets', '$sets'),
          if (prs > 0) _stat('PRs', '$prs'),
          if (dur != null) _stat('Duration', _fmtDuration(dur)),
        ]),
      ],
    );
  }

  Widget _cardioBody(BuildContext context, Map<String, dynamic> payload) {
    final tt = Theme.of(context).textTheme;
    final distM = (payload['distanceM'] as num?)?.toDouble();
    final dur = (payload['durationSeconds'] as num?)?.toInt();
    final pace = (payload['avgPaceSPerKm'] as num?)?.toDouble();
    final hr = (payload['avgHr'] as num?)?.toInt();
    final kcal = (payload['kcal'] as num?)?.toInt();
    final type = payload['type']?.toString() ?? item.post.type;

    String headline;
    if (distM != null) {
      headline = '${(distM / 1000).toStringAsFixed(2)} km '
          '${type[0].toUpperCase()}${type.substring(1)}';
    } else {
      headline = type;
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(headline,
            style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        Wrap(spacing: 16, runSpacing: 4, children: [
          if (dur != null) _stat('Duration', _fmtDuration(dur)),
          if (pace != null) _stat('Pace', _fmtPace(pace)),
          if (hr != null) _stat('Avg HR', '$hr bpm'),
          if (kcal != null) _stat('Kcal', '$kcal'),
        ]),
      ],
    );
  }

  Widget _evolutionBody(BuildContext context, Map<String, dynamic> payload) {
    final tt = Theme.of(context).textTheme;
    final weight = (payload['weightKg'] as num?)?.toDouble();
    final bf = (payload['bodyFatPct'] as num?)?.toDouble();
    final method = payload['bfMethod']?.toString();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('New evaluation',
            style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        Wrap(spacing: 16, runSpacing: 4, children: [
          if (weight != null) _stat('Weight', '${weight.toStringAsFixed(1)} kg'),
          if (bf != null) _stat('Body fat', '${bf.toStringAsFixed(1)} %'),
          if (method != null) _stat('Method', method.replaceAll('_', ' ')),
        ]),
      ],
    );
  }

  Widget _manualBody(BuildContext context, String? title, String? note) {
    final tt = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (title != null && title.isNotEmpty)
          Text(title,
              style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        if (note != null && note.isNotEmpty) ...[
          if (title != null && title.isNotEmpty) const SizedBox(height: 4),
          Text(note, style: tt.bodyMedium),
        ],
        if ((title == null || title.isEmpty) && (note == null || note.isEmpty))
          Text('(empty post)', style: tt.bodySmall),
      ],
    );
  }

  // ── small bits ──────────────────────────────────────────────────────

  Widget _stat(String label, String value) {
    return Builder(builder: (context) {
      final tt = Theme.of(context).textTheme;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: tt.bodySmall),
          Text(value,
              style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
        ],
      );
    });
  }

  Widget _action(
    BuildContext context, {
    required IconData icon,
    required Color color,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        child: Row(
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 4),
            Text(label, style: Theme.of(context).textTheme.bodyMedium),
          ],
        ),
      ),
    );
  }

  Widget _visibilityBadge(BuildContext context, String visibility) {
    if (visibility == 'public') return const SizedBox.shrink();
    final cs = Theme.of(context).colorScheme;
    final icon = visibility == 'private' ? Icons.lock_outline : Icons.group_outlined;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Icon(icon, size: 14, color: cs.onSurfaceVariant),
    );
  }

  IconData _iconForType(String type) {
    switch (type) {
      case 'workout':
        return Icons.fitness_center;
      case 'run':
        return Icons.directions_run;
      case 'cycle':
        return Icons.directions_bike;
      case 'evolution':
        return Icons.show_chart;
      default:
        return Icons.chat_bubble_outline;
    }
  }

  static String _fmtDuration(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    if (m >= 60) {
      final h = m ~/ 60;
      final mm = m % 60;
      return '${h}h ${mm.toString().padLeft(2, '0')}m';
    }
    return '$m:${s.toString().padLeft(2, '0')}';
  }

  static String _fmtPace(double secondsPerKm) {
    final m = secondsPerKm ~/ 60;
    final s = secondsPerKm.toInt() % 60;
    return '$m:${s.toString().padLeft(2, '0')}/km';
  }
}
