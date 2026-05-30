import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/coach_api_service.dart';

/// Sheet for creating an assignment for one athlete. MVP version covers the
/// freeform `notes` + `scheduledFor` path (no template / plan picker yet —
/// those land with later coach-side authoring stories). Returns `true` on
/// successful create.
class AssignSheet extends StatefulWidget {
  const AssignSheet({super.key, required this.athleteId});

  final int athleteId;

  @override
  State<AssignSheet> createState() => _AssignSheetState();
}

class _AssignSheetState extends State<AssignSheet> {
  String _type = 'workout';
  DateTime? _scheduledFor;
  final _notesCtrl = TextEditingController();
  bool _saving = false;
  String? _errorText;

  @override
  void dispose() {
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _scheduledFor ?? now,
      firstDate: now.subtract(const Duration(days: 365)),
      lastDate: now.add(const Duration(days: 365)),
    );
    if (picked != null) setState(() => _scheduledFor = picked);
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _errorText = null;
    });
    try {
      await CoachApiService.createAssignment(
        athleteId: widget.athleteId,
        type: _type,
        scheduledFor: _scheduledFor,
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? 'Couldn\'t create assignment.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t create assignment.';
      });
    }
  }

  String _fmtDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

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
          Text('New assignment',
              style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(
                  value: 'workout',
                  label: Text('Workout'),
                  icon: Icon(Icons.fitness_center)),
              ButtonSegment(
                  value: 'diet',
                  label: Text('Diet'),
                  icon: Icon(Icons.restaurant_outlined)),
              ButtonSegment(
                  value: 'eval',
                  label: Text('Eval'),
                  icon: Icon(Icons.analytics_outlined)),
            ],
            selected: {_type},
            onSelectionChanged: (s) => setState(() => _type = s.first),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _pickDate,
            icon: const Icon(Icons.event),
            label: Text(_scheduledFor == null
                ? 'Pick a date (optional)'
                : _fmtDate(_scheduledFor!)),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _notesCtrl,
            maxLines: 3,
            maxLength: 2000,
            decoration: const InputDecoration(
              labelText: 'Notes',
              border: OutlineInputBorder(),
            ),
          ),
          if (_errorText != null) ...[
            const SizedBox(height: 4),
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
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Create'),
          ),
        ],
      ),
    );
  }
}
