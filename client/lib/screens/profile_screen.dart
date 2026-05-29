import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/public_profile_response.dart';
import '../services/api/social_api_service.dart';
import '../services/secure_storage_service.dart';
import '../widgets/avatar.dart';
import '../widgets/follow_button.dart';

/// Public profile screen — header card with avatar/name/bio, a counters row
/// (Following / Followers / Sessions), and a follow button. The button is
/// hidden when the viewer is looking at their own profile. Sessions count is a
/// placeholder until AH-036 lands.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.handle});

  final String handle;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _loading = true;
  String? _errorText;
  PublicProfileResponse? _profile;
  bool _isMe = false;

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
      // Kick both reads off in parallel — the cached-user read hits secure
      // storage, the profile read hits the network. Await independently so
      // each keeps its own type.
      final profileFuture = SocialApiService.profileByHandle(widget.handle);
      final meFuture = SecureStorageService.getCachedUser();
      final profile = await profileFuture;
      final me = await meFuture;
      if (!mounted) return;
      setState(() {
        _profile = profile;
        _isMe = me != null && me.handle == widget.handle;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load profile.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load profile.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_profile == null ? 'Profile' : '@${_profile!.user.handle}'),
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
    final profile = _profile!;
    final user = profile.user;
    final tt = Theme.of(context).textTheme;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Avatar(fullName: user.fullName, hue: user.avatarHue, size: 72),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.fullName,
                      style: tt.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 2),
                    Text('@${user.handle}', style: tt.bodyMedium),
                  ],
                ),
              ),
            ],
          ),
          if ((user.bio ?? '').trim().isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(user.bio!.trim(), style: tt.bodyMedium),
          ],
          const SizedBox(height: 20),
          _CountersRow(
            following: profile.following,
            followers: profile.followers,
            // Sessions count isn't on the profile DTO yet — AH-036 will
            // surface it. Render a placeholder dash so the row stays balanced.
            sessions: null,
          ),
          const SizedBox(height: 20),
          if (!_isMe)
            Center(
              child: FollowButton(
                userId: user.id,
                isFollowing: profile.iFollow,
                onChanged: (nowFollowing) {
                  // Keep the local counter in sync so the row updates
                  // immediately after toggling.
                  setState(() {
                    final delta = nowFollowing ? 1 : -1;
                    _profile = PublicProfileResponse(
                      user: profile.user,
                      followers: (profile.followers + delta)
                          .clamp(0, 1 << 31),
                      following: profile.following,
                      iFollow: nowFollowing,
                    );
                  });
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _CountersRow extends StatelessWidget {
  const _CountersRow({
    required this.following,
    required this.followers,
    required this.sessions,
  });

  final int following;
  final int followers;
  final int? sessions;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _Counter(label: 'Following', value: '$following'),
        _Counter(label: 'Followers', value: '$followers'),
        _Counter(label: 'Sessions', value: sessions == null ? '—' : '$sessions'),
      ],
    );
  }
}

class _Counter extends StatelessWidget {
  const _Counter({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Column(
      children: [
        Text(value,
            style: tt.titleLarge?.copyWith(fontWeight: FontWeight.w700) ??
                const TextStyle(fontWeight: FontWeight.w700, fontSize: 20)),
        const SizedBox(height: 2),
        Text(label, style: tt.bodySmall),
      ],
    );
  }
}
