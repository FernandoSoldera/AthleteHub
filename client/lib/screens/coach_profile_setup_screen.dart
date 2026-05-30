import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/coach_profile.dart';
import '../services/api/coach_api_service.dart';

/// Coach profile setup — headline + years experience. Both fields optional
/// (the backend allows them to be null). On save, returns the updated
/// profile via Navigator.pop.
class CoachProfileSetupScreen extends StatefulWidget {
  const CoachProfileSetupScreen({super.key});

  @override
  State<CoachProfileSetupScreen> createState() =>
      _CoachProfileSetupScreenState();
}

class _CoachProfileSetupScreenState extends State<CoachProfileSetupScreen> {
  bool _loading = true;
  bool _saving = false;
  String? _errorText;
  CoachProfile? _profile;
  final _headlineCtrl = TextEditingController();
  final _yearsCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _headlineCtrl.dispose();
    _yearsCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final p = await CoachApiService.myCoachProfile();
      if (!mounted) return;
      setState(() {
        _profile = p;
        _headlineCtrl.text = p.headline ?? '';
        _yearsCtrl.text = p.yearsExperience?.toString() ?? '';
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load profile.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load profile.';
        _loading = false;
      });
    }
  }

  Future<void> _save() async {
    final headline = _headlineCtrl.text.trim();
    final yearsStr = _yearsCtrl.text.trim();
    int? years;
    if (yearsStr.isNotEmpty) {
      years = int.tryParse(yearsStr);
      if (years == null || years < 0 || years > 80) {
        setState(() => _errorText = 'Years must be a number from 0 to 80.');
        return;
      }
    }
    setState(() {
      _saving = true;
      _errorText = null;
    });
    try {
      final p = await CoachApiService.updateMyCoachProfile(
        headline: headline,
        yearsExperience: years,
      );
      if (!mounted) return;
      Navigator.of(context).pop(p);
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? 'Couldn\'t save profile.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t save profile.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Coach profile')),
      body: SafeArea(child: _buildBody()),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_profile == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_errorText ?? 'Couldn\'t load profile.',
                  textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: _load, child: const Text('Try again')),
            ],
          ),
        ),
      );
    }
    final p = _profile!;
    final tt = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _Stat(label: 'Athletes', value: '${p.athleteCount}'),
            _Stat(
                label: 'Rating',
                value: p.ratingCount == 0
                    ? '—'
                    : '${p.ratingAvg?.toStringAsFixed(1) ?? '—'} (${p.ratingCount})'),
          ],
        ),
        const SizedBox(height: 20),
        Text('Headline',
            style: tt.labelLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        TextField(
          controller: _headlineCtrl,
          maxLength: 200,
          decoration: const InputDecoration(
            hintText: 'e.g. Strength + hypertrophy coach',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 8),
        Text('Years experience',
            style: tt.labelLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        TextField(
          controller: _yearsCtrl,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(
            hintText: '0–80',
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
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Save'),
        ),
      ],
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Column(
      children: [
        Text(value,
            style: tt.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 2),
        Text(label, style: tt.bodySmall),
      ],
    );
  }
}
