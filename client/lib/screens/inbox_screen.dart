import 'dart:async';

import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/conversation.dart';
import '../services/api/messaging_api_service.dart';
import '../widgets/avatar.dart';
import 'chat_screen.dart';

/// Inbox — coach & athlete-shared thread list. Refreshes on resume and
/// while open via a light poll (every 8s) so new messages bubble to the
/// top without a manual pull. Pull to refresh for an immediate refetch.
class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key});

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> with WidgetsBindingObserver {
  bool _loading = true;
  String? _errorText;
  List<Conversation> _items = const [];
  Timer? _poll;

  static const _pollInterval = Duration(seconds: 8);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    _startPolling();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _load(silent: true);
    }
  }

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(_pollInterval, (_) => _load(silent: true));
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _errorText = null;
      });
    }
    try {
      final page = await MessagingApiService.inbox(limit: 50);
      if (!mounted) return;
      setState(() {
        _items = page.items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        if (!silent) _errorText = ex.message ?? 'Couldn\'t load inbox.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        if (!silent) _errorText = 'Couldn\'t load inbox.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Messages')),
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
                onPressed: () => _load(),
                child: const Text('Try again'),
              ),
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
            Center(child: Text('No conversations yet.')),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: _items.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (_, i) => _ConversationTile(
          conversation: _items[i],
          onOpened: () async {
            await Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => ChatScreen(conversation: _items[i]),
            ));
            if (!mounted) return;
            _load(silent: true);
          },
        ),
      ),
    );
  }
}

class _ConversationTile extends StatelessWidget {
  const _ConversationTile({required this.conversation, required this.onOpened});

  final Conversation conversation;
  final VoidCallback onOpened;

  @override
  Widget build(BuildContext context) {
    final peer = conversation.peer;
    final tt = Theme.of(context).textTheme;
    final preview = conversation.lastMessagePreview ?? 'No messages yet';
    return ListTile(
      leading: peer == null
          ? const CircleAvatar(child: Icon(Icons.person_outline))
          : Avatar(fullName: peer.fullName, hue: peer.avatarHue, size: 44),
      title: Text(peer?.fullName ?? 'Conversation #${conversation.id}'),
      subtitle: Text(
        preview,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: conversation.unreadCount > 0
            ? tt.bodyMedium?.copyWith(fontWeight: FontWeight.w700)
            : null,
      ),
      trailing: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          if (conversation.lastMessageAt != null)
            Text(_relativeTime(conversation.lastMessageAt!),
                style: tt.bodySmall),
          if (conversation.unreadCount > 0) ...[
            const SizedBox(height: 4),
            _UnreadBadge(count: conversation.unreadCount),
          ],
        ],
      ),
      onTap: onOpened,
    );
  }

  String _relativeTime(DateTime when) {
    final now = DateTime.now();
    final diff = now.difference(when);
    if (diff.inSeconds < 60) return 'now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m';
    if (diff.inHours < 24) return '${diff.inHours}h';
    if (diff.inDays < 7) return '${diff.inDays}d';
    return '${when.year}-${when.month.toString().padLeft(2, '0')}-${when.day.toString().padLeft(2, '0')}';
  }
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      constraints: const BoxConstraints(minWidth: 22, minHeight: 22),
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: cs.primary,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Center(
        child: Text(
          count > 99 ? '99+' : '$count',
          style: TextStyle(
            color: cs.onPrimary,
            fontWeight: FontWeight.w700,
            fontSize: 12,
          ),
        ),
      ),
    );
  }
}
