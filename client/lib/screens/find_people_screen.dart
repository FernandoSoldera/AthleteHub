import 'dart:async';

import 'package:flutter/material.dart';

import '../models/public_user.dart';
import '../models/responses/api_error_response.dart';
import '../models/responses/cursor_page.dart';
import '../models/responses/suggested_user.dart';
import '../services/api/social_api_service.dart';
import '../widgets/avatar.dart';
import '../widgets/follow_button.dart';
import 'profile_screen.dart';

/// Find People — search by name/handle when the field has text, otherwise show
/// suggestions with mutual-follow counts. Matches the original `FindScreen`
/// from the design (segmented list, follow/following chips).
class FindPeopleScreen extends StatefulWidget {
  const FindPeopleScreen({super.key});

  @override
  State<FindPeopleScreen> createState() => _FindPeopleScreenState();
}

class _FindPeopleScreenState extends State<FindPeopleScreen> {
  final _searchController = TextEditingController();
  Timer? _debounce;

  bool _loading = true;
  String? _errorText;

  // Either suggestions (when query is empty) or search results.
  List<SuggestedUser> _suggestions = const [];
  List<PublicUser> _searchResults = const [];

  String get _query => _searchController.text.trim();

  @override
  void initState() {
    super.initState();
    _loadSuggestions();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  void _onQueryChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 250), () {
      if (_query.isEmpty) {
        _loadSuggestions();
      } else {
        _runSearch();
      }
    });
  }

  Future<void> _loadSuggestions() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final CursorPage<SuggestedUser> page = await SocialApiService.suggestions();
      if (!mounted) return;
      setState(() {
        _suggestions = page.items;
        _searchResults = const [];
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load suggestions.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load suggestions.';
        _loading = false;
      });
    }
  }

  Future<void> _runSearch() async {
    final q = _query;
    if (q.isEmpty) return;
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final CursorPage<PublicUser> page = await SocialApiService.search(q);
      if (!mounted) return;
      setState(() {
        _searchResults = page.items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Search failed.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Search failed.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final inSearchMode = _query.isNotEmpty;

    return Scaffold(
      appBar: AppBar(title: const Text('Find people')),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: TextField(
                controller: _searchController,
                onChanged: _onQueryChanged,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: 'Search by name or @handle',
                  prefixIcon: const Icon(Icons.search, size: 18),
                  suffixIcon: inSearchMode
                      ? IconButton(
                          icon: const Icon(Icons.close, size: 18),
                          onPressed: () {
                            _searchController.clear();
                            _onQueryChanged('');
                          },
                        )
                      : null,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
              ),
            ),
            if (!inSearchMode)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    'Suggested for you',
                    style: tt.labelSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.08 * 14,
                        ) ??
                        const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
              ),
            Expanded(child: _buildList(inSearchMode)),
          ],
        ),
      ),
    );
  }

  Widget _buildList(bool inSearchMode) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_errorText != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(_errorText!, textAlign: TextAlign.center),
        ),
      );
    }
    if (inSearchMode) {
      if (_searchResults.isEmpty) {
        return const Center(child: Text('No matches.'));
      }
      return ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
        itemCount: _searchResults.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, i) {
          final u = _searchResults[i];
          return _UserRow(
            id: u.id,
            fullName: u.fullName,
            handle: u.handle,
            hue: u.avatarHue,
            subtitle: '@${u.handle}',
            initiallyFollowing: false,
          );
        },
      );
    }
    if (_suggestions.isEmpty) {
      return const Center(child: Text('No suggestions right now.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      itemCount: _suggestions.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (_, i) {
        final s = _suggestions[i];
        final mutualLabel = s.mutualCount == 1 ? '1 mutual' : '${s.mutualCount} mutual';
        return _UserRow(
          id: s.id,
          fullName: s.fullName,
          handle: s.handle,
          hue: s.avatarHue,
          subtitle: '@${s.handle} · $mutualLabel',
          initiallyFollowing: false,
        );
      },
    );
  }
}

class _UserRow extends StatelessWidget {
  const _UserRow({
    required this.id,
    required this.fullName,
    required this.handle,
    required this.subtitle,
    required this.initiallyFollowing,
    this.hue,
  });

  final int id;
  final String fullName;
  final String handle;
  final String subtitle;
  final bool initiallyFollowing;
  final int? hue;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return InkWell(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => ProfileScreen(handle: handle)),
      ),
      borderRadius: BorderRadius.circular(14),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
        child: Row(
          children: [
            Avatar(fullName: fullName, hue: hue),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(fullName,
                      style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w600) ??
                          const TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 2),
                  Text(subtitle, style: tt.bodySmall),
                ],
              ),
            ),
            const SizedBox(width: 8),
            FollowButton(userId: id, isFollowing: initiallyFollowing),
          ],
        ),
      ),
    );
  }
}
