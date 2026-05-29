import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/day_response.dart';
import '../models/responses/diary_entry.dart';
import '../services/api/diet_api_service.dart';
import '../widgets/macro_ring.dart';
import 'add_food_sheet.dart';

/// Diet tab — day-by-day view of macro totals + diary entries grouped by
/// meal label. The macro ring shows raw consumed values when no active
/// plan is set (AH-052 returns target: null in that case); when a plan
/// is active the ring fills proportionally to target. FAB opens the
/// Add-Food sheet.
class DietScreen extends StatefulWidget {
  const DietScreen({super.key});

  @override
  State<DietScreen> createState() => _DietScreenState();
}

class _DietScreenState extends State<DietScreen> {
  DateTime _date = DateTime.now();
  bool _loading = true;
  String? _errorText;
  DayResponse? _day;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      // Use a date with the time stripped so AH-052's server-zone parsing
      // of YYYY-MM-DD lands on the right day regardless of local clock.
      final day = await DietApiService.day(date: _date);
      if (!mounted) return;
      setState(() {
        _day = day;
        _loading = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _errorText = ex.message ?? 'Couldn\'t load diet day.';
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorText = 'Couldn\'t load diet day.';
        _loading = false;
      });
    }
  }

  void _shiftDay(int days) {
    setState(() => _date = _date.add(Duration(days: days)));
    _load();
  }

  Future<void> _addFood({String? defaultMealLabel}) async {
    final added = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => AddFoodSheet(defaultMealLabel: defaultMealLabel),
    );
    if (added == true) await _load();
  }

  Future<void> _deleteEntry(DiaryEntry entry) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete entry?'),
        content: Text('${entry.foodName} — ${entry.amount.toStringAsFixed(0)} '
            '${entry.unit}'),
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
      await DietApiService.deleteDiaryEntry(entry.id);
      await _load();
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t delete entry.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Diet'),
        centerTitle: false,
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _addFood(),
        icon: const Icon(Icons.add),
        label: const Text('Add food'),
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
    final day = _day!;
    final grouped = _groupByMeal(day.entries);
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
        children: [
          _DayPicker(
            date: day.date,
            onPrev: () => _shiftDay(-1),
            onNext: () => _shiftDay(1),
            onTap: _pickDate,
          ),
          const SizedBox(height: 16),
          Center(
            child: MacroRing(totals: day.totals, target: day.target),
          ),
          const SizedBox(height: 12),
          _MacroLegend(day: day),
          const SizedBox(height: 8),
          if (!day.hasTarget)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                'No active diet plan — showing raw totals. '
                'Plan support arrives with coaching.',
                style: Theme.of(context).textTheme.bodySmall,
                textAlign: TextAlign.center,
              ),
            ),
          const SizedBox(height: 12),
          if (day.entries.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Text(
                'Nothing logged yet. Tap "Add food" to start.',
                textAlign: TextAlign.center,
              ),
            )
          else
            for (final entry in grouped.entries) ...[
              _MealHeader(
                label: entry.key,
                onAdd: () => _addFood(defaultMealLabel: entry.key),
              ),
              for (final e in entry.value)
                _DiaryEntryTile(
                  entry: e,
                  onDelete: () => _deleteEntry(e),
                ),
              const SizedBox(height: 8),
            ],
        ],
      ),
    );
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) {
      setState(() => _date = picked);
      _load();
    }
  }

  /// Preserves the order of first appearance for stable rendering.
  Map<String, List<DiaryEntry>> _groupByMeal(List<DiaryEntry> entries) {
    final out = <String, List<DiaryEntry>>{};
    for (final e in entries) {
      final label = (e.mealLabel == null || e.mealLabel!.isEmpty)
          ? 'Other'
          : e.mealLabel!;
      out.putIfAbsent(label, () => []).add(e);
    }
    return out;
  }
}

// ── day picker ────────────────────────────────────────────────────────

class _DayPicker extends StatelessWidget {
  const _DayPicker({
    required this.date,
    required this.onPrev,
    required this.onNext,
    required this.onTap,
  });

  final DateTime date;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final today = DateTime.now();
    final isToday = date.year == today.year &&
        date.month == today.month &&
        date.day == today.day;
    return Row(
      children: [
        IconButton(onPressed: onPrev, icon: const Icon(Icons.chevron_left)),
        Expanded(
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Center(
                child: Column(
                  children: [
                    Text(
                      isToday
                          ? 'Today'
                          : DateFormat.EEEE().format(date.toLocal()),
                      style: tt.bodyMedium,
                    ),
                    Text(
                      DateFormat.yMMMMd().format(date.toLocal()),
                      style:
                          tt.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        IconButton(onPressed: onNext, icon: const Icon(Icons.chevron_right)),
      ],
    );
  }
}

// ── macro legend ──────────────────────────────────────────────────────

class _MacroLegend extends StatelessWidget {
  const _MacroLegend({required this.day});

  final DayResponse day;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: [
        _legendChip(
          context,
          color: const Color(0xFF6BB37C),
          label: 'Protein',
          consumed: day.totals.proteinG,
          target: day.target?.proteinG,
        ),
        _legendChip(
          context,
          color: const Color(0xFFE0A24B),
          label: 'Carbs',
          consumed: day.totals.carbG,
          target: day.target?.carbG,
        ),
        _legendChip(
          context,
          color: const Color(0xFFD9627B),
          label: 'Fat',
          consumed: day.totals.fatG,
          target: day.target?.fatG,
        ),
      ],
    );
  }

  Widget _legendChip(BuildContext context,
      {required Color color,
      required String label,
      required double? consumed,
      required double? target}) {
    final tt = Theme.of(context).textTheme;
    final value = consumed ?? 0;
    final body = target == null
        ? '${value.toStringAsFixed(0)} g'
        : '${value.toStringAsFixed(0)} / ${target.toStringAsFixed(0)} g';
    return Column(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(height: 4),
        Text(label, style: tt.bodySmall),
        Text(body, style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
      ],
    );
  }
}

// ── meal sections ─────────────────────────────────────────────────────

class _MealHeader extends StatelessWidget {
  const _MealHeader({required this.label, required this.onAdd});

  final String label;
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 12, 4, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: tt.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          TextButton.icon(
            onPressed: onAdd,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Add'),
          ),
        ],
      ),
    );
  }
}

class _DiaryEntryTile extends StatelessWidget {
  const _DiaryEntryTile({required this.entry, required this.onDelete});

  final DiaryEntry entry;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Dismissible(
      key: ValueKey('diary-${entry.id}'),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) async {
        onDelete();
        // Always return false — let the parent re-render via _load().
        return false;
      },
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 16),
        color: cs.error,
        child: Icon(Icons.delete_outline, color: cs.onError),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    entry.foodName,
                    style: tt.bodyLarge
                        ?.copyWith(fontWeight: FontWeight.w600),
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    '${entry.amount.toStringAsFixed(0)} ${entry.unit} · '
                    '${(entry.macros.kcal ?? 0).toStringAsFixed(0)} kcal · '
                    'P ${(entry.macros.proteinG ?? 0).toStringAsFixed(1)} '
                    '/ C ${(entry.macros.carbG ?? 0).toStringAsFixed(1)} '
                    '/ F ${(entry.macros.fatG ?? 0).toStringAsFixed(1)}',
                    style: tt.bodySmall,
                  ),
                ],
              ),
            ),
            Text(DateFormat.Hm().format(entry.eatenAt.toLocal()),
                style: tt.bodySmall),
          ],
        ),
      ),
    );
  }
}
