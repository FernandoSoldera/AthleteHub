import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/social_api_service.dart';

/// Optimistically-toggling follow / following button. Calls the API on tap
/// and reverts on failure. Reports the new state to the parent via
/// [onChanged] so list rows can keep their copy of the flag in sync.
class FollowButton extends StatefulWidget {
  const FollowButton({
    super.key,
    required this.userId,
    required this.isFollowing,
    this.onChanged,
  });

  final int userId;
  final bool isFollowing;
  final ValueChanged<bool>? onChanged;

  @override
  State<FollowButton> createState() => _FollowButtonState();
}

class _FollowButtonState extends State<FollowButton> {
  late bool _following = widget.isFollowing;
  bool _busy = false;

  @override
  void didUpdateWidget(covariant FollowButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isFollowing != widget.isFollowing) {
      _following = widget.isFollowing;
    }
  }

  Future<void> _toggle() async {
    if (_busy) return;
    final next = !_following;
    setState(() {
      _busy = true;
      _following = next;
    });
    try {
      if (next) {
        await SocialApiService.follow(widget.userId);
      } else {
        await SocialApiService.unfollow(widget.userId);
      }
      widget.onChanged?.call(next);
    } on ApiException catch (ex) {
      // Revert the optimistic flip.
      if (mounted) setState(() => _following = !next);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(ex.message ?? 'Couldn\'t update follow.')),
        );
      }
    } catch (_) {
      if (mounted) setState(() => _following = !next);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final label = _following ? 'Following' : 'Follow';
    return SizedBox(
      height: 32,
      child: _following
          ? OutlinedButton(
              onPressed: _busy ? null : _toggle,
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 14),
              ),
              child: Text(label, style: const TextStyle(fontSize: 12)),
            )
          : FilledButton(
              onPressed: _busy ? null : _toggle,
              style: FilledButton.styleFrom(
                backgroundColor: cs.primary,
                foregroundColor: cs.onPrimary,
                padding: const EdgeInsets.symmetric(horizontal: 14),
              ),
              child: Text(label, style: const TextStyle(fontSize: 12)),
            ),
    );
  }
}
