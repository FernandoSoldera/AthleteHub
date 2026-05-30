import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/comment.dart';
import '../services/api/feed_api_service.dart';
import '../services/secure_storage_service.dart';
import '../widgets/avatar.dart';

/// Chronological thread for one post — oldest first, with a compose box
/// pinned to the bottom. Swipe-left on a comment to delete (only allowed
/// on the viewer's own comments; backend returns 404 otherwise).
class CommentThreadScreen extends StatefulWidget {
  const CommentThreadScreen({super.key, required this.postId});

  final int postId;

  @override
  State<CommentThreadScreen> createState() => _CommentThreadScreenState();
}

class _CommentThreadScreenState extends State<CommentThreadScreen> {
  bool _loading = true;
  String? _errorText;
  final List<Comment> _comments = [];
  int? _nextCursor;
  bool _loadingMore = false;

  int? _myUserId;

  final _composeCtrl = TextEditingController();
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _loadInitial();
    _loadMyUserId();
  }

  Future<void> _loadMyUserId() async {
    final me = await SecureStorageService.getCachedUser();
    if (!mounted) return;
    setState(() => _myUserId = me?.id);
  }

  Future<void> _loadInitial() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final page = await FeedApiService.listComments(widget.postId, limit: 30);
      if (!mounted) return;
      setState(() {
        _comments
          ..clear()
          ..addAll(page.items);
        _nextCursor = page.nextCursor == null ? null : int.parse(page.nextCursor!);
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load comments.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load comments.';
        _loading = false;
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || _nextCursor == null) return;
    setState(() => _loadingMore = true);
    try {
      final page = await FeedApiService.listComments(
        widget.postId,
        cursor: _nextCursor,
        limit: 30,
      );
      if (!mounted) return;
      setState(() {
        _comments.addAll(page.items);
        _nextCursor = page.nextCursor == null ? null : int.parse(page.nextCursor!);
        _loadingMore = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  Future<void> _send() async {
    final body = _composeCtrl.text.trim();
    if (body.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      final created = await FeedApiService.addComment(widget.postId, body);
      if (!mounted) return;
      setState(() {
        _comments.add(created); // chronological — newest at the end
        _composeCtrl.clear();
        _sending = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() => _sending = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t post comment.')),
      );
    }
  }

  Future<void> _delete(Comment c) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete comment?'),
        content: const Text('This will hide your comment from the thread.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await FeedApiService.deleteComment(c.id);
      if (!mounted) return;
      setState(() => _comments.removeWhere((x) => x.id == c.id));
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t delete comment.')),
      );
    }
  }

  @override
  void dispose() {
    _composeCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Comments')),
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
    return Column(
      children: [
        Expanded(
          child: _comments.isEmpty
              ? const Center(child: Text('No comments yet.'))
              : NotificationListener<ScrollNotification>(
                  onNotification: (n) {
                    if (n.metrics.pixels >= n.metrics.maxScrollExtent - 200) {
                      _loadMore();
                    }
                    return false;
                  },
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                    itemCount: _comments.length + (_loadingMore ? 1 : 0),
                    itemBuilder: (_, i) {
                      if (i >= _comments.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Center(child: CircularProgressIndicator()),
                        );
                      }
                      return _CommentTile(
                        comment: _comments[i],
                        isMine: _myUserId != null &&
                            _comments[i].author?.id == _myUserId,
                        onDelete: () => _delete(_comments[i]),
                      );
                    },
                  ),
                ),
        ),
        const Divider(height: 1),
        _ComposeBox(
          controller: _composeCtrl,
          sending: _sending,
          onSend: _send,
        ),
      ],
    );
  }
}

class _CommentTile extends StatelessWidget {
  const _CommentTile({
    required this.comment,
    required this.isMine,
    required this.onDelete,
  });

  final Comment comment;
  final bool isMine;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final tile = Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Avatar(
            fullName: comment.author?.fullName ?? '?',
            hue: comment.author?.avatarHue,
            size: 32,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Flexible(
                    child: Text(
                      comment.author?.fullName ?? 'Unknown',
                      style:
                          tt.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    DateFormat.MMMd().add_jm().format(comment.createdAt.toLocal()),
                    style: tt.bodySmall,
                  ),
                ]),
                const SizedBox(height: 2),
                Text(comment.body, style: tt.bodyMedium),
              ],
            ),
          ),
        ],
      ),
    );
    if (!isMine) return tile;
    return Dismissible(
      key: ValueKey('comment-${comment.id}'),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) async {
        onDelete();
        return false;
      },
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 16),
        color: cs.error,
        child: Icon(Icons.delete_outline, color: cs.onError),
      ),
      child: tile,
    );
  }
}

class _ComposeBox extends StatelessWidget {
  const _ComposeBox({
    required this.controller,
    required this.sending,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool sending;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        12, 8, 12, 8 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              minLines: 1,
              maxLines: 4,
              decoration: InputDecoration(
                hintText: 'Add a comment…',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                ),
                isDense: true,
              ),
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            tooltip: 'Send',
            icon: sending
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send),
            onPressed: sending ? null : onSend,
          ),
        ],
      ),
    );
  }
}
