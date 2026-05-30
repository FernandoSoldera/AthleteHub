import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/coach_api_service.dart';

/// Sheet for sending a coach invite by athlete handle. Returns `true` on
/// success so the caller can refresh the roster.
class InviteAthleteSheet extends StatefulWidget {
  const InviteAthleteSheet({super.key});

  @override
  State<InviteAthleteSheet> createState() => _InviteAthleteSheetState();
}

class _InviteAthleteSheetState extends State<InviteAthleteSheet> {
  final _handleCtrl = TextEditingController();
  bool _saving = false;
  String? _errorText;

  @override
  void dispose() {
    _handleCtrl.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    var handle = _handleCtrl.text.trim();
    if (handle.startsWith('@')) handle = handle.substring(1);
    if (handle.length < 3) {
      setState(() => _errorText = 'Handle is too short.');
      return;
    }
    setState(() {
      _saving = true;
      _errorText = null;
    });
    try {
      await CoachApiService.sendInvite(handle);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? 'Couldn\'t send invite.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t send invite.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Padding(
      padding: EdgeInsets.fromLTRB(
          20, 8, 20, 24 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Invite athlete',
              style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          TextField(
            controller: _handleCtrl,
            autofocus: true,
            decoration: const InputDecoration(
              prefixText: '@',
              labelText: 'Handle',
              border: OutlineInputBorder(),
            ),
          ),
          if (_errorText != null) ...[
            const SizedBox(height: 8),
            Text(_errorText!,
                style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _saving ? null : _send,
            child: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Send invite'),
          ),
        ],
      ),
    );
  }
}
