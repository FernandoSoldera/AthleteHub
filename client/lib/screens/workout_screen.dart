import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/exercise_set.dart';
import '../models/responses/session_exercise.dart';
import '../models/responses/workout_session.dart';
import '../services/api/training_api_service.dart';

/// Live workout — exercise list with per-set rows (weight × reps + done),
/// client-side rest timer that starts when a set flips to done, and a
/// finish button that posts to the server and shows the PR + volume
/// summary.
///
/// State management is plain `setState`. The set ops use the granular
/// PATCH contract (AH-033): tap "done" → optimistic flip locally, fire
/// an `upsert` op, reconcile from the server's full WorkoutSession
/// response so set ids + completedAt stay in sync.
class WorkoutScreen extends StatefulWidget {
  const WorkoutScreen({super.key, required this.sessionId});

  final int sessionId;

  @override
  State<WorkoutScreen> createState() => _WorkoutScreenState();
}

class _WorkoutScreenState extends State<WorkoutScreen> {
  WorkoutSession? _session;
  bool _loading = true;
  String? _errorText;

  // Rest timer — counts down from [_restSeconds] when a set is completed.
  static const int _restSeconds = 90;
  Timer? _restTimer;
  int _restRemaining = 0;

  // Pending patch ops we haven't shipped yet because another patch is
  // in-flight — we coalesce so an over-eager tap doesn't fire 5 in flight.
  final List<Map<String, dynamic>> _pendingOps = [];
  bool _patching = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _restTimer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final session = await TrainingApiService.getSession(widget.sessionId);
      if (!mounted) return;
      setState(() {
        _session = session;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load session.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load session.';
        _loading = false;
      });
    }
  }

  // ── set ops ─────────────────────────────────────────────────────────

  void _queueOp(Map<String, dynamic> op) {
    _pendingOps.add(op);
    _flushOps();
  }

  Future<void> _flushOps() async {
    if (_patching || _pendingOps.isEmpty) return;
    _patching = true;

    while (_pendingOps.isNotEmpty) {
      final batch = List<Map<String, dynamic>>.from(_pendingOps);
      _pendingOps.clear();
      try {
        final updated = await TrainingApiService.patchSession(widget.sessionId, batch);
        if (!mounted) return;
        setState(() => _session = updated);
      } on ApiException catch (ex) {
        if (!mounted) return;
        // Roll back to whatever the server thinks.
        try {
          final fresh = await TrainingApiService.getSession(widget.sessionId);
          if (mounted) setState(() => _session = fresh);
        } catch (_) {}
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ex.message ?? 'Couldn\'t save set.')),
          );
        }
        break;
      } catch (_) {
        break;
      }
    }
    _patching = false;
  }

  void _toggleDone({
    required SessionExercise exercise,
    required ExerciseSet set,
    required bool nextDone,
  }) {
    _queueOp({
      'op': 'upsert',
      'sessionExerciseId': exercise.id,
      'setNumber': set.setNumber,
      'weightKg': set.weightKg,
      'reps': set.reps,
      'done': nextDone,
    });
    if (nextDone) _startRest();
  }

  void _updateSetField({
    required SessionExercise exercise,
    required int setNumber,
    double? weightKg,
    int? reps,
    bool? done,
  }) {
    final op = <String, dynamic>{
      'op': 'upsert',
      'sessionExerciseId': exercise.id,
      'setNumber': setNumber,
    };
    if (weightKg != null) op['weightKg'] = weightKg;
    if (reps != null) op['reps'] = reps;
    if (done != null) op['done'] = done;
    _queueOp(op);
  }

  void _addSet(SessionExercise exercise) {
    final nextSetNumber =
        exercise.sets.isEmpty ? 1 : exercise.sets.last.setNumber + 1;
    _queueOp({
      'op': 'upsert',
      'sessionExerciseId': exercise.id,
      'setNumber': nextSetNumber,
      'done': false,
    });
  }

  void _deleteSet(SessionExercise exercise, ExerciseSet set) {
    _queueOp({
      'op': 'delete',
      'sessionExerciseId': exercise.id,
      'setNumber': set.setNumber,
    });
  }

  // ── rest timer ──────────────────────────────────────────────────────

  void _startRest() {
    _restTimer?.cancel();
    setState(() => _restRemaining = _restSeconds);
    _restTimer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) {
        t.cancel();
        return;
      }
      setState(() {
        _restRemaining--;
        if (_restRemaining <= 0) t.cancel();
      });
    });
  }

  void _stopRest() {
    _restTimer?.cancel();
    setState(() => _restRemaining = 0);
  }

  // ── finish ──────────────────────────────────────────────────────────

  Future<void> _finish() async {
    try {
      final finished = await TrainingApiService.finishSession(widget.sessionId);
      if (!mounted) return;
      setState(() => _session = finished);
      _stopRest();
      await _showFinishedSheet(finished);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t finish session.')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Couldn\'t finish session.')),
      );
    }
  }

  Future<void> _showFinishedSheet(WorkoutSession s) {
    return showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (_) {
        final tt = Theme.of(context).textTheme;
        return Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Session complete 🎉',
                  style: tt.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 16),
              _statRow('Total volume', '${s.totalVolumeKg.toStringAsFixed(2)} kg'),
              _statRow('Sets completed', '${s.totalSets}'),
              _statRow('PRs hit', '${s.prCount}'),
              _statRow('Duration', _formatDuration(s.durationSeconds ?? 0)),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Done'),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _statRow(String label, String value) {
    final tt = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(child: Text(label, style: tt.bodyMedium)),
          Text(value, style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }

  // ── derived numbers ─────────────────────────────────────────────────

  double get _runningVolume {
    final s = _session;
    if (s == null) return 0;
    var v = 0.0;
    for (final se in s.exercises) {
      for (final set in se.sets) {
        if (set.done && set.weightKg != null && set.reps != null) {
          v += set.weightKg! * set.reps!;
        }
      }
    }
    return v;
  }

  int get _doneSets {
    final s = _session;
    if (s == null) return 0;
    var n = 0;
    for (final se in s.exercises) {
      for (final set in se.sets) {
        if (set.done) n++;
      }
    }
    return n;
  }

  int get _totalSetRows {
    final s = _session;
    if (s == null) return 0;
    return s.exercises.fold<int>(0, (acc, se) => acc + se.sets.length);
  }

  // ── build ──────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_session?.title ?? 'Workout'),
        actions: [
          if (_session != null && _session!.isInProgress)
            TextButton(
              onPressed: _finish,
              child: const Text('Finish'),
            ),
        ],
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
              OutlinedButton(onPressed: _load, child: const Text('Try again')),
            ],
          ),
        ),
      );
    }
    final session = _session!;
    final progress = _totalSetRows == 0 ? 0.0 : _doneSets / _totalSetRows;

    return Column(
      children: [
        // Progress + running volume + rest timer.
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(child: Text(
                    'Volume: ${_runningVolume.toStringAsFixed(0)} kg',
                    style: Theme.of(context).textTheme.bodyMedium,
                  )),
                  Text('$_doneSets / $_totalSetRows sets',
                      style: Theme.of(context).textTheme.bodyMedium),
                ],
              ),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: LinearProgressIndicator(value: progress, minHeight: 6),
              ),
              if (_restRemaining > 0) ...[
                const SizedBox(height: 8),
                _RestTimerBar(remaining: _restRemaining, total: _restSeconds, onStop: _stopRest),
              ],
            ],
          ),
        ),
        Expanded(
          child: session.exercises.isEmpty
              ? const Center(child: Text('No exercises — add some from a template next time.'))
              : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                  itemCount: session.exercises.length,
                  itemBuilder: (_, i) => _ExerciseCard(
                    exercise: session.exercises[i],
                    onToggleDone: (set, next) => _toggleDone(
                      exercise: session.exercises[i],
                      set: set,
                      nextDone: next,
                    ),
                    onUpdate: ({required setNumber, weightKg, reps}) => _updateSetField(
                      exercise: session.exercises[i],
                      setNumber: setNumber,
                      weightKg: weightKg,
                      reps: reps,
                    ),
                    onAddSet: () => _addSet(session.exercises[i]),
                    onDeleteSet: (set) => _deleteSet(session.exercises[i], set),
                  ),
                ),
        ),
      ],
    );
  }

  static String _formatDuration(int seconds) {
    final m = (seconds ~/ 60).toString().padLeft(2, '0');
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}

// ── widgets ────────────────────────────────────────────────────────────

class _RestTimerBar extends StatelessWidget {
  const _RestTimerBar({
    required this.remaining,
    required this.total,
    required this.onStop,
  });

  final int remaining;
  final int total;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: cs.primaryContainer,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Icon(Icons.timer_outlined, size: 18, color: cs.onPrimaryContainer),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Rest — ${remaining}s',
              style: TextStyle(color: cs.onPrimaryContainer, fontWeight: FontWeight.w600),
            ),
          ),
          IconButton(
            tooltip: 'Skip rest',
            icon: Icon(Icons.close, color: cs.onPrimaryContainer),
            onPressed: onStop,
          ),
        ],
      ),
    );
  }
}

class _ExerciseCard extends StatelessWidget {
  const _ExerciseCard({
    required this.exercise,
    required this.onToggleDone,
    required this.onUpdate,
    required this.onAddSet,
    required this.onDeleteSet,
  });

  final SessionExercise exercise;
  final void Function(ExerciseSet set, bool nextDone) onToggleDone;
  final void Function({required int setNumber, double? weightKg, int? reps}) onUpdate;
  final VoidCallback onAddSet;
  final void Function(ExerciseSet set) onDeleteSet;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    exercise.name,
                    style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                if (exercise.scheme != null)
                  Text(exercise.scheme!, style: tt.bodySmall),
              ],
            ),
            const SizedBox(height: 8),
            for (final set in exercise.sets)
              _SetRow(
                set: set,
                onToggleDone: (next) => onToggleDone(set, next),
                onWeightChanged: (v) => onUpdate(setNumber: set.setNumber, weightKg: v),
                onRepsChanged: (v) => onUpdate(setNumber: set.setNumber, reps: v),
                onDelete: () => onDeleteSet(set),
              ),
            const SizedBox(height: 4),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: onAddSet,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Add set'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SetRow extends StatefulWidget {
  const _SetRow({
    required this.set,
    required this.onToggleDone,
    required this.onWeightChanged,
    required this.onRepsChanged,
    required this.onDelete,
  });

  final ExerciseSet set;
  final void Function(bool nextDone) onToggleDone;
  final void Function(double weightKg) onWeightChanged;
  final void Function(int reps) onRepsChanged;
  final VoidCallback onDelete;

  @override
  State<_SetRow> createState() => _SetRowState();
}

class _SetRowState extends State<_SetRow> {
  late final TextEditingController _weight;
  late final TextEditingController _reps;

  @override
  void initState() {
    super.initState();
    _weight = TextEditingController(
      text: widget.set.weightKg == null
          ? ''
          : _trimZero(widget.set.weightKg!),
    );
    _reps = TextEditingController(
      text: widget.set.reps?.toString() ?? '',
    );
  }

  @override
  void didUpdateWidget(covariant _SetRow oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Reflect server-side changes if we didn't originate them locally.
    final newWeight = widget.set.weightKg == null ? '' : _trimZero(widget.set.weightKg!);
    if (newWeight != _weight.text) _weight.text = newWeight;
    final newReps = widget.set.reps?.toString() ?? '';
    if (newReps != _reps.text) _reps.text = newReps;
  }

  @override
  void dispose() {
    _weight.dispose();
    _reps.dispose();
    super.dispose();
  }

  static String _trimZero(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toString();
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 24,
            child: Text(
              '${widget.set.setNumber}',
              style: tt.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(child: _smallField(
            controller: _weight,
            hint: 'kg',
            decimal: true,
            onChangedNumber: (n) {
              if (n != null) widget.onWeightChanged(n);
            },
          )),
          const SizedBox(width: 8),
          Expanded(child: _smallField(
            controller: _reps,
            hint: 'reps',
            decimal: false,
            onChangedNumber: (n) {
              if (n != null) widget.onRepsChanged(n.toInt());
            },
          )),
          const SizedBox(width: 8),
          if (widget.set.pr)
            const Padding(
              padding: EdgeInsets.only(right: 4),
              child: Icon(Icons.emoji_events, size: 18, color: Colors.amber),
            ),
          Checkbox(
            value: widget.set.done,
            onChanged: (v) => widget.onToggleDone(v ?? false),
            activeColor: cs.primary,
          ),
          IconButton(
            tooltip: 'Delete set',
            icon: const Icon(Icons.delete_outline, size: 18),
            onPressed: widget.onDelete,
          ),
        ],
      ),
    );
  }

  Widget _smallField({
    required TextEditingController controller,
    required String hint,
    required bool decimal,
    required void Function(double? parsed) onChangedNumber,
  }) {
    return SizedBox(
      height: 38,
      child: TextField(
        controller: controller,
        keyboardType: TextInputType.numberWithOptions(decimal: decimal),
        inputFormatters: [
          FilteringTextInputFormatter.allow(RegExp(decimal ? r'[0-9.]' : r'[0-9]')),
        ],
        textAlign: TextAlign.center,
        decoration: InputDecoration(
          isDense: true,
          hintText: hint,
          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
        ),
        onChanged: (s) => onChangedNumber(double.tryParse(s.trim())),
      ),
    );
  }
}
