import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/feed_api_service.dart';

/// Compose sheet for a manual post — title + note + visibility picker.
/// Used from the Feed FAB. Returns `true` on successful save so the
/// caller can refresh the feed.
class NewPostSheet extends StatefulWidget {
  const NewPostSheet({super.key});

  @override
  State<NewPostSheet> createState() => _NewPostSheetState();
}

class _NewPostSheetState extends State<NewPostSheet> {
  final _titleCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  String _visibility = 'followers';

  bool _saving = false;
  String? _errorText;

  @override
  void dispose() {
    _titleCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final title = _titleCtrl.text.trim();
    final note = _noteCtrl.text.trim();
    if (title.isEmpty && note.isEmpty) {
      setState(() => _errorText = 'Add a title or a note.');
      return;
    }
    setState(() {
      _saving = true;
      _errorText = null;
    });
    try {
      await FeedApiService.createManualPost(
        title: title.isEmpty ? null : title,
        note: note.isEmpty ? null : note,
        visibility: _visibility,
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? 'Couldn\'t publish.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t publish.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        20, 8, 20, 24 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('New post', style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          TextField(
            controller: _titleCtrl,
            maxLength: 200,
            decoration: const InputDecoration(
              labelText: 'Title (optional)',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _noteCtrl,
            maxLines: 4,
            maxLength: 2000,
            decoration: const InputDecoration(
              labelText: 'Note',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          Text('Visibility', style: tt.labelLarge),
          const SizedBox(height: 6),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(
                value: 'public',
                label: Text('Public'),
                icon: Icon(Icons.public),
              ),
              ButtonSegment(
                value: 'followers',
                label: Text('Followers'),
                icon: Icon(Icons.group_outlined),
              ),
              ButtonSegment(
                value: 'private',
                label: Text('Private'),
                icon: Icon(Icons.lock_outline),
              ),
            ],
            selected: {_visibility},
            onSelectionChanged: (s) => setState(() => _visibility = s.first),
          ),
          if (_errorText != null) ...[
            const SizedBox(height: 8),
            Text(_errorText!,
                style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Publish'),
          ),
        ],
      ),
    );
  }
}
