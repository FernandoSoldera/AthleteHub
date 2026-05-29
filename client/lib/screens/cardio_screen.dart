import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/training_api_service.dart';

/// Log a cardio activity — run / walk / cycle. Distance + duration are
/// required; everything else (HR, pace, power, elevation, kcal, notes) is
/// optional because different sources fill different subsets. The server
/// validates per-field and returns `VALIDATION_FAILED` with a field-error
/// map when something's off — we surface those inline.
class CardioScreen extends StatefulWidget {
  const CardioScreen({super.key});

  @override
  State<CardioScreen> createState() => _CardioScreenState();
}

class _CardioScreenState extends State<CardioScreen> {
  final _formKey = GlobalKey<FormState>();
  String _type = 'run';

  final _distanceKm = TextEditingController();
  final _durationMin = TextEditingController();
  final _avgHr = TextEditingController();
  final _maxHr = TextEditingController();
  final _elevation = TextEditingController();
  final _kcal = TextEditingController();
  final _notes = TextEditingController();

  bool _saving = false;
  String? _errorText;
  Map<String, String> _fieldErrors = const {};

  @override
  void dispose() {
    _distanceKm.dispose();
    _durationMin.dispose();
    _avgHr.dispose();
    _maxHr.dispose();
    _elevation.dispose();
    _kcal.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _errorText = null;
      _fieldErrors = const {};
    });

    final distanceKm = double.tryParse(_distanceKm.text.trim()) ?? 0;
    final durationMin = double.tryParse(_durationMin.text.trim()) ?? 0;

    try {
      await TrainingApiService.createCardio(
        type: _type,
        distanceM: distanceKm * 1000,
        durationSeconds: (durationMin * 60).round(),
        avgHr: int.tryParse(_avgHr.text.trim()),
        maxHr: int.tryParse(_maxHr.text.trim()),
        elevationGainM: double.tryParse(_elevation.text.trim()),
        kcal: int.tryParse(_kcal.text.trim()),
        notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? 'Couldn\'t save activity.';
        _fieldErrors = ex.fieldErrors ?? const {};
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t save activity.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Log cardio')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            children: [
              Text('Activity', style: tt.labelLarge),
              const SizedBox(height: 8),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'run', label: Text('Run'), icon: Icon(Icons.directions_run)),
                  ButtonSegment(value: 'walk', label: Text('Walk'), icon: Icon(Icons.directions_walk)),
                  ButtonSegment(value: 'cycle', label: Text('Cycle'), icon: Icon(Icons.directions_bike)),
                ],
                selected: {_type},
                onSelectionChanged: (s) => setState(() => _type = s.first),
              ),
              const SizedBox(height: 16),
              Row(children: [
                Expanded(child: _numField(
                  controller: _distanceKm,
                  label: 'Distance (km)',
                  required: true,
                  decimal: true,
                  errorField: 'distanceM',
                )),
                const SizedBox(width: 12),
                Expanded(child: _numField(
                  controller: _durationMin,
                  label: 'Duration (min)',
                  required: true,
                  decimal: true,
                  errorField: 'durationSeconds',
                )),
              ]),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(child: _numField(
                  controller: _avgHr, label: 'Avg HR (bpm)',
                  errorField: 'avgHr',
                )),
                const SizedBox(width: 12),
                Expanded(child: _numField(
                  controller: _maxHr, label: 'Max HR (bpm)',
                  errorField: 'maxHr',
                )),
              ]),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(child: _numField(
                  controller: _elevation, label: 'Elevation gain (m)',
                  decimal: true, errorField: 'elevationGainM',
                )),
                const SizedBox(width: 12),
                Expanded(child: _numField(
                  controller: _kcal, label: 'Calories (kcal)',
                  errorField: 'kcal',
                )),
              ]),
              const SizedBox(height: 12),
              TextFormField(
                controller: _notes,
                maxLines: 3,
                maxLength: 2000,
                decoration: InputDecoration(
                  labelText: 'Notes',
                  border: const OutlineInputBorder(),
                  errorText: _fieldErrors['notes'],
                ),
              ),
              if (_errorText != null) ...[
                const SizedBox(height: 12),
                Text(_errorText!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Save activity'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _numField({
    required TextEditingController controller,
    required String label,
    bool required = false,
    bool decimal = false,
    String? errorField,
  }) {
    return TextFormField(
      controller: controller,
      keyboardType: TextInputType.numberWithOptions(decimal: decimal),
      inputFormatters: [
        FilteringTextInputFormatter.allow(RegExp(decimal ? r'[0-9.]' : r'[0-9]')),
      ],
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        errorText: errorField == null ? null : _fieldErrors[errorField],
      ),
      validator: (v) {
        if (!required) return null;
        final t = v?.trim() ?? '';
        if (t.isEmpty) return 'Required';
        final parsed = double.tryParse(t);
        if (parsed == null) return 'Invalid number';
        if (parsed < 0) return 'Must be ≥ 0';
        return null;
      },
    );
  }
}
