import 'dart:async';

import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/conversation.dart';
import '../models/responses/message.dart';
import '../services/api/messaging_api_service.dart';
import '../services/secure_storage_service.dart';

/// One-on-one chat. Messages list paginates newest-first; composer at the
/// bottom. Polls for new messages every 4s while open and on resume,
/// auto-advances the read pointer on mount + after every send.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.conversation});

  final Conversation conversation;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> with WidgetsBindingObserver {
  static const _pollInterval = Duration(seconds: 4);

  bool _loading = true;
  String? _errorText;
  bool _sending = false;
  String? _sendError;
  int? _myUserId;
  final _composeCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();

  // Newest first (matches API order). The chat is rendered with a
  // reverse: true list so item[0] sits at the bottom.
  List<Message> _items = const [];
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _bootstrap();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _composeCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh(silent: true);
      MessagingApiService.markRead(widget.conversation.id).catchError((_) {});
    }
  }

  Future<void> _bootstrap() async {
    final me = await SecureStorageService.getCachedUser();
    if (!mounted) return;
    setState(() => _myUserId = me?.id);
    await _refresh();
    await MessagingApiService.markRead(widget.conversation.id).catchError((_) {});
    _startPolling();
  }

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(_pollInterval, (_) => _refresh(silent: true));
  }

  Future<void> _refresh({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _errorText = null;
      });
    }
    try {
      final page = await MessagingApiService.messages(
        conversationId: widget.conversation.id,
        limit: 50,
      );
      if (!mounted) return;
      setState(() {
        _items = page.items;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        if (!silent) _errorText = ex.message ?? 'Couldn\'t load messages.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        if (!silent) _errorText = 'Couldn\'t load messages.';
        _loading = false;
      });
    }
  }

  Future<void> _send() async {
    final body = _composeCtrl.text.trim();
    if (body.isEmpty) return;
    setState(() {
      _sending = true;
      _sendError = null;
    });
    try {
      final msg = await MessagingApiService.sendMessage(
        conversationId: widget.conversation.id,
        body: body,
      );
      if (!mounted) return;
      setState(() {
        _items = [msg, ..._items];
        _composeCtrl.clear();
        _sending = false;
      });
      // Scroll to the new message (top in reverse list view).
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollCtrl.hasClients) {
          _scrollCtrl.animateTo(
            0,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
          );
        }
      });
      // Coach probably wants to keep typing — read pointer auto-advances
      // server-side for sender; nothing more needed here.
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _sending = false;
        _sendError = ex.message ?? 'Couldn\'t send.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _sending = false;
        _sendError = 'Couldn\'t send.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final peer = widget.conversation.peer;
    return Scaffold(
      appBar: AppBar(
        title: Text(peer?.fullName ?? 'Conversation #${widget.conversation.id}'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(child: _buildMessages()),
            const Divider(height: 1),
            _Composer(
              controller: _composeCtrl,
              sending: _sending,
              errorText: _sendError,
              onSend: _send,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessages() {
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
                  onPressed: () => _refresh(), child: const Text('Try again')),
            ],
          ),
        ),
      );
    }
    if (_items.isEmpty) {
      return const Center(child: Text('Say hi 👋'));
    }
    return ListView.builder(
      controller: _scrollCtrl,
      reverse: true,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      itemCount: _items.length,
      itemBuilder: (_, i) {
        final msg = _items[i];
        final mine = _myUserId != null && msg.senderId == _myUserId;
        return _MessageBubble(message: msg, mine: mine);
      },
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.mine});

  final Message message;
  final bool mine;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final bg = mine ? cs.primary : cs.surfaceContainerHighest;
    final fg = mine ? cs.onPrimary : cs.onSurface;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: mine ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width * 0.75,
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.only(
                topLeft: const Radius.circular(16),
                topRight: const Radius.circular(16),
                bottomLeft: Radius.circular(mine ? 16 : 4),
                bottomRight: Radius.circular(mine ? 4 : 16),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(message.body, style: TextStyle(color: fg)),
                const SizedBox(height: 2),
                Text(
                  _fmtTime(message.createdAt),
                  style: TextStyle(
                    fontSize: 10,
                    color: fg.withValues(alpha: 0.65),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _fmtTime(DateTime t) {
    final h = t.hour.toString().padLeft(2, '0');
    final m = t.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}

class _Composer extends StatelessWidget {
  const _Composer({
    required this.controller,
    required this.sending,
    required this.errorText,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool sending;
  final String? errorText;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
          12, 8, 12, 12 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (errorText != null) ...[
            Text(errorText!,
                style: TextStyle(color: Theme.of(context).colorScheme.error)),
            const SizedBox(height: 4),
          ],
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: controller,
                  maxLines: 4,
                  minLines: 1,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(
                    hintText: 'Message…',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
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
        ],
      ),
    );
  }
}
