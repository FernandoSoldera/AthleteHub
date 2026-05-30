import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/feed_item.dart';
import '../services/api/feed_api_service.dart';
import '../widgets/feed_card.dart';
import 'comment_thread_screen.dart';
import 'find_people_screen.dart';
import 'new_post_sheet.dart';
import 'profile_screen.dart';

/// Home timeline tab — paged FeedCard list with type filter chips,
/// optimistic like, comment-tap navigation, and an extended FAB that
/// opens the manual-post compose sheet. Replaces the placeholder in
/// `main_shell`.
class FeedScreen extends StatefulWidget {
  const FeedScreen({super.key});

  @override
  State<FeedScreen> createState() => _FeedScreenState();
}

class _FeedScreenState extends State<FeedScreen> {
  bool _loading = true;
  String? _errorText;
  final List<FeedItem> _items = [];
  int? _nextCursor;
  bool _loadingMore = false;

  String? _selectedType; // null → all types

  @override
  void initState() {
    super.initState();
    _loadInitial();
  }

  Future<void> _loadInitial() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final page = await FeedApiService.homeFeed(limit: 20, type: _selectedType);
      if (!mounted) return;
      setState(() {
        _items
          ..clear()
          ..addAll(page.items);
        _nextCursor = page.nextCursor == null ? null : int.parse(page.nextCursor!);
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load feed.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load feed.';
        _loading = false;
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || _nextCursor == null) return;
    setState(() => _loadingMore = true);
    try {
      final page = await FeedApiService.homeFeed(
        cursor: _nextCursor,
        limit: 20,
        type: _selectedType,
      );
      if (!mounted) return;
      setState(() {
        _items.addAll(page.items);
        _nextCursor = page.nextCursor == null ? null : int.parse(page.nextCursor!);
        _loadingMore = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  Future<void> _toggleLike(int index) async {
    final item = _items[index];
    final nextLiked = !item.iLiked;
    final delta = nextLiked ? 1 : -1;
    setState(() {
      _items[index] = FeedItem(
        post: item.post.copyWith(likeCount: item.post.likeCount + delta),
        author: item.author,
        iLiked: nextLiked,
      );
    });
    try {
      if (nextLiked) {
        await FeedApiService.like(item.post.id);
      } else {
        await FeedApiService.unlike(item.post.id);
      }
    } on ApiException catch (ex) {
      // Revert.
      if (!mounted) return;
      setState(() {
        _items[index] = item;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t update like.')),
      );
    }
  }

  Future<void> _openComments(FeedItem item) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CommentThreadScreen(postId: item.post.id),
      ),
    );
    // No reload on return — the per-card count may drift slightly until
    // the next refresh, but that's a small UX cost vs re-fetching the
    // whole feed.
  }

  void _openAuthor(FeedItem item) {
    final handle = item.author?.handle;
    if (handle == null) return;
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => ProfileScreen(handle: handle)),
    );
  }

  Future<void> _openCompose() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const NewPostSheet(),
    );
    if (created == true) await _loadInitial();
  }

  void _setType(String? type) {
    setState(() => _selectedType = type);
    _loadInitial();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Feed'),
        centerTitle: false,
        actions: [
          IconButton(
            tooltip: 'Find people',
            icon: const Icon(Icons.person_search_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const FindPeopleScreen()),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openCompose,
        icon: const Icon(Icons.edit),
        label: const Text('New post'),
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
              OutlinedButton(
                onPressed: _loadInitial,
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _loadInitial,
      child: Column(
        children: [
          _TypeFilter(selected: _selectedType, onSelected: _setType),
          Expanded(
            child: _items.isEmpty
                ? ListView(
                    children: const [
                      SizedBox(height: 120),
                      Center(
                        child: Padding(
                          padding: EdgeInsets.all(24),
                          child: Text(
                            'Nothing in your feed yet.\n'
                            'Follow people or finish a workout to see posts here.',
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ),
                    ],
                  )
                : NotificationListener<ScrollNotification>(
                    onNotification: (n) {
                      if (n.metrics.pixels >= n.metrics.maxScrollExtent - 400) {
                        _loadMore();
                      }
                      return false;
                    },
                    child: ListView.builder(
                      padding: const EdgeInsets.only(bottom: 96),
                      itemCount: _items.length + (_loadingMore ? 1 : 0),
                      itemBuilder: (_, i) {
                        if (i >= _items.length) {
                          return const Padding(
                            padding: EdgeInsets.symmetric(vertical: 12),
                            child: Center(child: CircularProgressIndicator()),
                          );
                        }
                        final item = _items[i];
                        return FeedCard(
                          item: item,
                          onLikeTap: () => _toggleLike(i),
                          onCommentTap: () => _openComments(item),
                          onAuthorTap: () => _openAuthor(item),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

// ── type filter ──────────────────────────────────────────────────────

class _TypeFilter extends StatelessWidget {
  const _TypeFilter({required this.selected, required this.onSelected});

  final String? selected;
  final void Function(String?) onSelected;

  static const _types = [
    ('all', 'All', null),
    ('workout', 'Workout', 'workout'),
    ('run', 'Run', 'run'),
    ('cycle', 'Cycle', 'cycle'),
    ('evolution', 'Evolution', 'evolution'),
    ('manual', 'Manual', 'manual'),
  ];

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        children: [
          for (final t in _types)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: ChoiceChip(
                label: Text(t.$2),
                selected: selected == t.$3,
                onSelected: (_) => onSelected(t.$3),
              ),
            ),
        ],
      ),
    );
  }
}
