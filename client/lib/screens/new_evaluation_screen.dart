import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/evaluation_measurement.dart';
import '../services/api/evaluation_api_service.dart';
import '../services/secure_storage_service.dart';

/// New evaluation form. Weight is required; body-fat method is optional
/// and reveals method-specific inputs:
///
///   * **None** → weight-only check-in. Optional "other measurements"
///     section so the user can still record circumferences for the
///     time-series graphs.
///   * **Manual** → body-fat % input.
///   * **Jackson-Pollock 7-site** → seven skinfold inputs (mm); requires
///     `users.sex` + `users.age` (pre-checked from secure storage so the
///     user gets a friendlier error than the backend's
///     `BF_MISSING_USER_FIELD`).
///   * **Navy** → neck + waist (+ hip for female) circumferences;
///     requires `users.sex` + `users.heightCm`.
///
/// Beyond the method-specific inputs, "other measurements" lets the user
/// add free-form point ids (per the schema design — `point_id` is TEXT
/// and the catalog isn't fixed).
class NewEvaluationScreen extends StatefulWidget {
  const NewEvaluationScreen({super.key});

  @override
  State<NewEvaluationScreen> createState() => _NewEvaluationScreenState();
}

class _NewEvaluationScreenState extends State<NewEvaluationScreen> {
  final _formKey = GlobalKey<FormState>();

  String? _bfMethod; // null | 'manual' | 'jackson_pollock_7' | 'navy'

  final _weight = TextEditingController();
  final _bodyFat = TextEditingController();
  final _notes = TextEditingController();

  // Method-specific controllers, keyed by point id.
  final Map<String, TextEditingController> _jp7 = {
    for (final p in _jp7Points) p: TextEditingController(),
  };
  final Map<String, TextEditingController> _navy = {
    'neck': TextEditingController(),
    'waist': TextEditingController(),
    'hip': TextEditingController(),
  };

  // Free-form measurements the user added explicitly.
  final List<_OtherMeasurement> _other = [];

  String? _userSex;
  int? _userAge;
  double? _userHeightCm;
  bool _loadingUser = true;

  bool _saving = false;
  String? _errorText;
  Map<String, String> _fieldErrors = const {};

  static const List<String> _jp7Points = [
    'chest', 'abdomen', 'thigh', 'tricep',
    'subscapular', 'suprailiac', 'midaxillary',
  ];

  @override
  void initState() {
    super.initState();
    _loadUserProfile();
  }

  Future<void> _loadUserProfile() async {
    final me = await SecureStorageService.getCachedUser();
    if (!mounted) return;
    setState(() {
      _userSex = me?.sex;
      _userAge = me?.age;
      _userHeightCm = me?.heightCm;
      _loadingUser = false;
    });
  }

  @override
  void dispose() {
    _weight.dispose();
    _bodyFat.dispose();
    _notes.dispose();
    for (final c in _jp7.values) {
      c.dispose();
    }
    for (final c in _navy.values) {
      c.dispose();
    }
    for (final m in _other) {
      m.controller.dispose();
    }
    super.dispose();
  }

  // ── method gating ───────────────────────────────────────────────────

  /// Returns a friendly error string when the chosen method needs profile
  /// fields the user hasn't filled in yet.
  String? _profileGateError() {
    switch (_bfMethod) {
      case 'jackson_pollock_7':
        if (_userSex == null) return 'Set your sex in profile settings first.';
        if (_userAge == null) return 'Set your age in profile settings first.';
        return null;
      case 'navy':
        if (_userSex == null) return 'Set your sex in profile settings first.';
        if (_userHeightCm == null) return 'Set your height in profile settings first.';
        return null;
      default:
        return null;
    }
  }

  bool get _navyNeedsHip => _userSex == 'female';

  // ── save ───────────────────────────────────────────────────────────

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final gate = _profileGateError();
    if (gate != null) {
      setState(() => _errorText = gate);
      return;
    }
    setState(() {
      _saving = true;
      _errorText = null;
      _fieldErrors = const {};
    });

    final measurements = _collectMeasurements();
    final weightKg = double.tryParse(_weight.text.trim()) ?? 0;
    double? bodyFatPct;
    if (_bfMethod == 'manual') {
      bodyFatPct = double.tryParse(_bodyFat.text.trim());
    }

    try {
      await EvaluationApiService.create(
        weightKg: weightKg,
        bfMethod: _bfMethod,
        bodyFatPct: bodyFatPct,
        notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
        measurements: measurements,
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? ex.code ?? 'Couldn\'t save evaluation.';
        _fieldErrors = ex.fieldErrors ?? const {};
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t save evaluation.';
      });
    }
  }

  List<EvaluationMeasurement> _collectMeasurements() {
    final out = <EvaluationMeasurement>[];

    if (_bfMethod == 'jackson_pollock_7') {
      for (final p in _jp7Points) {
        final v = double.tryParse(_jp7[p]!.text.trim());
        if (v != null) {
          out.add(EvaluationMeasurement(
              pointId: p, kind: 'skinfold', unit: 'mm', value: v));
        }
      }
    } else if (_bfMethod == 'navy') {
      for (final p in ['neck', 'waist', if (_navyNeedsHip) 'hip']) {
        final v = double.tryParse(_navy[p]!.text.trim());
        if (v != null) {
          out.add(EvaluationMeasurement(
              pointId: p, kind: 'circumference', unit: 'cm', value: v));
        }
      }
    }

    // Always include explicit "other" measurements — but skip duplicates
    // of point ids already supplied by the method section so the backend's
    // duplicate-point validation doesn't reject the payload.
    final taken = out.map((m) => m.pointId).toSet();
    for (final o in _other) {
      if (o.pointId.isEmpty || taken.contains(o.pointId)) continue;
      final v = double.tryParse(o.controller.text.trim());
      if (v == null) continue;
      out.add(EvaluationMeasurement(
          pointId: o.pointId, kind: o.kind, unit: o.unit, value: v));
    }
    return out;
  }

  // ── build ──────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    if (_loadingUser) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('New evaluation')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            children: [
              _numField(
                controller: _weight,
                label: 'Weight (kg)',
                required: true,
                decimal: true,
                errorField: 'weightKg',
              ),
              const SizedBox(height: 16),
              Text('Body-fat method', style: tt.labelLarge),
              const SizedBox(height: 8),
              DropdownButtonFormField<String?>(
                initialValue: _bfMethod,
                items: const [
                  DropdownMenuItem(value: null, child: Text('None (weight only)')),
                  DropdownMenuItem(value: 'manual', child: Text('Manual entry')),
                  DropdownMenuItem(
                      value: 'jackson_pollock_7',
                      child: Text('Jackson-Pollock 7-site')),
                  DropdownMenuItem(value: 'navy', child: Text('Navy formula')),
                ],
                decoration: const InputDecoration(border: OutlineInputBorder()),
                onChanged: (v) => setState(() => _bfMethod = v),
              ),
              if (_bfMethod == 'manual') ...[
                const SizedBox(height: 12),
                _numField(
                  controller: _bodyFat,
                  label: 'Body fat (%)',
                  required: true,
                  decimal: true,
                  errorField: 'bodyFatPct',
                ),
              ],
              if (_bfMethod == 'jackson_pollock_7') _buildJp7Section(),
              if (_bfMethod == 'navy') _buildNavySection(),
              const SizedBox(height: 20),
              Row(children: [
                Expanded(
                    child: Text('Other measurements',
                        style: tt.labelLarge)),
                TextButton.icon(
                  onPressed: _addOtherMeasurement,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Add'),
                ),
              ]),
              if (_other.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(
                    'Tap "Add" to record a circumference or skinfold beyond '
                    'the body-fat method requirements.',
                    style: tt.bodySmall,
                  ),
                )
              else
                ..._other.asMap().entries.map((e) => _OtherMeasurementRow(
                      key: ValueKey(e.value),
                      measurement: e.value,
                      onChanged: () => setState(() {}),
                      onRemove: () => setState(() {
                        e.value.controller.dispose();
                        _other.removeAt(e.key);
                      }),
                    )),
              const SizedBox(height: 16),
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
                Text(_errorText!, style: TextStyle(color: cs.error)),
              ],
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Save evaluation'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── method-specific sections ───────────────────────────────────────

  Widget _buildJp7Section() {
    final gate = _profileGateError();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Skinfold sites (mm)',
              style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 6),
          if (gate != null) _profileGateBanner(gate),
          for (final p in _jp7Points)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: _numField(
                controller: _jp7[p]!,
                label: _humanize(p),
                required: true,
                decimal: true,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildNavySection() {
    final gate = _profileGateError();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Circumferences (cm)',
              style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 6),
          if (gate != null) _profileGateBanner(gate),
          for (final p in ['neck', 'waist', if (_navyNeedsHip) 'hip'])
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: _numField(
                controller: _navy[p]!,
                label: _humanize(p),
                required: true,
                decimal: true,
              ),
            ),
        ],
      ),
    );
  }

  Widget _profileGateBanner(String message) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      margin: const EdgeInsets.only(bottom: 4),
      decoration: BoxDecoration(
        color: cs.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(message, style: TextStyle(color: cs.onErrorContainer)),
    );
  }

  // ── other-measurements ───────────────────────────────────────────────

  Future<void> _addOtherMeasurement() async {
    final picked = await showModalBottomSheet<_OtherMeasurement>(
      context: context,
      showDragHandle: true,
      builder: (_) => const _MeasurementPicker(),
    );
    if (picked == null) return;
    setState(() => _other.add(picked));
  }

  // ── shared field widget ──────────────────────────────────────────────

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

  static String _humanize(String pointId) {
    if (pointId.isEmpty) return pointId;
    return pointId.replaceAll('_', ' ').replaceFirstMapped(
        RegExp(r'^.'), (m) => m.group(0)!.toUpperCase());
  }
}

class _OtherMeasurement {
  _OtherMeasurement({required this.pointId, required this.kind, required this.unit})
      : controller = TextEditingController();

  final String pointId;
  String kind;
  String unit;
  final TextEditingController controller;
}

class _OtherMeasurementRow extends StatelessWidget {
  const _OtherMeasurementRow({
    super.key,
    required this.measurement,
    required this.onChanged,
    required this.onRemove,
  });

  final _OtherMeasurement measurement;
  final VoidCallback onChanged;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${measurement.pointId} · ${measurement.kind}',
                  style: tt.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
          SizedBox(
            width: 110,
            child: TextField(
              controller: measurement.controller,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
              ],
              decoration: InputDecoration(
                isDense: true,
                hintText: measurement.unit,
                contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
              onChanged: (_) => onChanged(),
            ),
          ),
          IconButton(
            tooltip: 'Remove',
            icon: const Icon(Icons.delete_outline, size: 18),
            onPressed: onRemove,
          ),
        ],
      ),
    );
  }
}

/// Modal sheet that picks a point id + kind + unit. Common circumference
/// and skinfold points are surfaced as chips; "Custom…" lets the user
/// type any point id.
class _MeasurementPicker extends StatefulWidget {
  const _MeasurementPicker();

  @override
  State<_MeasurementPicker> createState() => _MeasurementPickerState();
}

class _MeasurementPickerState extends State<_MeasurementPicker> {
  static const _circumferences = [
    'neck', 'chest', 'waist', 'hip',
    'arm_l', 'arm_r', 'thigh_l', 'thigh_r',
    'calf_l', 'calf_r', 'forearm_l', 'forearm_r',
  ];
  static const _skinfolds = [
    'chest', 'abdomen', 'thigh', 'tricep', 'biceps',
    'subscapular', 'suprailiac', 'midaxillary',
  ];

  String _kind = 'circumference';
  String? _selectedPoint;
  final _customPoint = TextEditingController();

  @override
  void dispose() {
    _customPoint.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final points = _kind == 'circumference' ? _circumferences : _skinfolds;
    final unit = _kind == 'circumference' ? 'cm' : 'mm';

    return Padding(
      padding: EdgeInsets.fromLTRB(
        20, 8, 20, 24 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Add measurement',
              style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'circumference', label: Text('Circumference')),
              ButtonSegment(value: 'skinfold', label: Text('Skinfold')),
            ],
            selected: {_kind},
            onSelectionChanged: (s) => setState(() {
              _kind = s.first;
              _selectedPoint = null;
            }),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final p in points)
                ChoiceChip(
                  label: Text(p.replaceAll('_', ' ')),
                  selected: _selectedPoint == p,
                  onSelected: (_) => setState(() {
                    _selectedPoint = p;
                    _customPoint.clear();
                  }),
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _customPoint,
            decoration: const InputDecoration(
              labelText: 'Custom point id',
              border: OutlineInputBorder(),
              hintText: 'e.g. ankle_r',
            ),
            onChanged: (_) => setState(() => _selectedPoint = null),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () {
              final point = _selectedPoint ?? _customPoint.text.trim();
              if (point.isEmpty) return;
              Navigator.of(context).pop(_OtherMeasurement(
                pointId: point,
                kind: _kind,
                unit: unit,
              ));
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
  }
}
