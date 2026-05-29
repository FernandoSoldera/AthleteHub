import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/responses/api_error_response.dart';
import '../models/responses/favorite.dart';
import '../models/responses/food.dart';
import '../services/api/diet_api_service.dart';

/// Modal bottom-sheet for adding a diary entry. Two tabs:
///
///   * **Search** — debounced food search across the catalog (global +
///     caller's customs); tapping a row opens the amount / unit / meal
///     label form for that food.
///   * **Favorites** — the caller's saved Quick-Add list; same pattern,
///     plus an unfavorite icon per row.
///
/// After picking a food, a small form appears below. Submit posts the
/// diary entry; an optional "save as favorite" checkbox calls the
/// favorite endpoint in parallel (idempotent, so a second tap is fine).
///
/// Returns `true` to the caller on successful save so the Diet screen
/// can re-load the day payload.
class AddFoodSheet extends StatefulWidget {
  const AddFoodSheet({super.key, this.defaultMealLabel});

  /// When provided, pre-fills the meal-label field so the user doesn't
  /// have to retype "Breakfast" three times.
  final String? defaultMealLabel;

  @override
  State<AddFoodSheet> createState() => _AddFoodSheetState();
}

class _AddFoodSheetState extends State<AddFoodSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _searchCtrl = TextEditingController();
  Timer? _debounce;

  bool _loadingSearch = false;
  String? _searchError;
  List<Food> _searchResults = const [];

  bool _loadingFavorites = false;
  String? _favoritesError;
  List<Favorite> _favorites = const [];

  Food? _picked;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _loadFavorites();
    _runSearch(); // initial: show catalog
  }

  @override
  void dispose() {
    _tabs.dispose();
    _debounce?.cancel();
    _searchCtrl.dispose();
    super.dispose();
  }

  // ── data loaders ────────────────────────────────────────────────────

  void _onQueryChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 250), _runSearch);
  }

  Future<void> _runSearch() async {
    final q = _searchCtrl.text.trim();
    setState(() {
      _loadingSearch = true;
      _searchError = null;
    });
    try {
      final page = await DietApiService.searchFoods(
          q: q.isEmpty ? null : q, limit: 30);
      if (!mounted) return;
      setState(() {
        _searchResults = page.items;
        _loadingSearch = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _searchError = ex.message ?? 'Search failed.';
        _loadingSearch = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _searchError = 'Search failed.';
        _loadingSearch = false;
      });
    }
  }

  Future<void> _loadFavorites() async {
    setState(() {
      _loadingFavorites = true;
      _favoritesError = null;
    });
    try {
      final page = await DietApiService.listFavorites(limit: 50);
      if (!mounted) return;
      setState(() {
        _favorites = page.items;
        _loadingFavorites = false;
      });
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _favoritesError = ex.message ?? 'Couldn\'t load favorites.';
        _loadingFavorites = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _favoritesError = 'Couldn\'t load favorites.';
        _loadingFavorites = false;
      });
    }
  }

  Future<void> _toggleFavorite(Food food, bool currentlyFavorited) async {
    try {
      if (currentlyFavorited) {
        await DietApiService.removeFavorite(food.id);
      } else {
        await DietApiService.addFavorite(food.id);
      }
      await _loadFavorites();
    } on ApiException catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ex.message ?? 'Couldn\'t update favorite.')),
      );
    }
  }

  // ── build ──────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final viewInsets = MediaQuery.of(context).viewInsets;

    return Padding(
      padding: EdgeInsets.only(bottom: viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.82,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      _picked == null ? 'Add food' : _picked!.name,
                      style: tt.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (_picked != null)
                    TextButton(
                      onPressed: () => setState(() => _picked = null),
                      child: const Text('Back'),
                    ),
                ],
              ),
            ),
            if (_picked == null) ...[
              TabBar(controller: _tabs, tabs: const [
                Tab(text: 'Search'),
                Tab(text: 'Favorites'),
              ]),
              Expanded(
                child: TabBarView(controller: _tabs, children: [
                  _buildSearchTab(),
                  _buildFavoritesTab(),
                ]),
              ),
            ] else
              Expanded(
                child: _AmountForm(
                  food: _picked!,
                  defaultMealLabel: widget.defaultMealLabel,
                  isFavorited: _isFavorite(_picked!.id),
                  onToggleFavorite: (next) async {
                    await _toggleFavorite(_picked!, !next ? true : false);
                    // After toggle, re-evaluate.
                  },
                  onSubmitted: () => Navigator.of(context).pop(true),
                ),
              ),
          ],
        ),
      ),
    );
  }

  bool _isFavorite(int foodId) {
    for (final f in _favorites) {
      if (f.food?.id == foodId) return true;
    }
    return false;
  }

  Widget _buildSearchTab() {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          child: TextField(
            controller: _searchCtrl,
            onChanged: _onQueryChanged,
            decoration: InputDecoration(
              hintText: 'Search foods (e.g. chicken)',
              prefixIcon: const Icon(Icons.search, size: 18),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
          ),
        ),
        Expanded(child: _buildSearchList()),
      ],
    );
  }

  Widget _buildSearchList() {
    if (_loadingSearch) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_searchError != null) {
      return Center(child: Text(_searchError!));
    }
    if (_searchResults.isEmpty) {
      return const Center(child: Text('No foods matched.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 16),
      itemCount: _searchResults.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (_, i) {
        final food = _searchResults[i];
        return _FoodTile(
          food: food,
          trailing: IconButton(
            tooltip: _isFavorite(food.id) ? 'Unfavorite' : 'Favorite',
            icon: Icon(
              _isFavorite(food.id) ? Icons.star : Icons.star_border,
              size: 20,
            ),
            onPressed: () => _toggleFavorite(food, _isFavorite(food.id)),
          ),
          onTap: () => setState(() => _picked = food),
        );
      },
    );
  }

  Widget _buildFavoritesTab() {
    if (_loadingFavorites) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_favoritesError != null) {
      return Center(child: Text(_favoritesError!));
    }
    if (_favorites.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No favorites yet. Tap the star on any food in Search to add it here.',
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 16),
      itemCount: _favorites.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (_, i) {
        final fav = _favorites[i];
        final food = fav.food;
        if (food == null) return const SizedBox.shrink();
        return _FoodTile(
          food: food,
          trailing: IconButton(
            tooltip: 'Unfavorite',
            icon: const Icon(Icons.star, size: 20),
            onPressed: () => _toggleFavorite(food, true),
          ),
          onTap: () => setState(() => _picked = food),
        );
      },
    );
  }
}

// ── tile ────────────────────────────────────────────────────────────

class _FoodTile extends StatelessWidget {
  const _FoodTile({
    required this.food,
    required this.trailing,
    required this.onTap,
  });

  final Food food;
  final Widget trailing;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          food.name,
                          style: tt.bodyLarge
                              ?.copyWith(fontWeight: FontWeight.w600),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (food.custom) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding:
                              const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.secondaryContainer,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text('Custom',
                              style: tt.labelSmall?.copyWith(
                                color: Theme.of(context)
                                    .colorScheme
                                    .onSecondaryContainer,
                              )),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${food.kcal.toStringAsFixed(0)} kcal · '
                    'P ${food.proteinG.toStringAsFixed(1)} '
                    '/ C ${food.carbG.toStringAsFixed(1)} '
                    '/ F ${food.fatG.toStringAsFixed(1)} '
                    'per ${food.servingSizeG.toStringAsFixed(0)} g',
                    style: tt.bodySmall,
                  ),
                ],
              ),
            ),
            trailing,
          ],
        ),
      ),
    );
  }
}

// ── amount form ────────────────────────────────────────────────────

class _AmountForm extends StatefulWidget {
  const _AmountForm({
    required this.food,
    required this.defaultMealLabel,
    required this.isFavorited,
    required this.onToggleFavorite,
    required this.onSubmitted,
  });

  final Food food;
  final String? defaultMealLabel;
  final bool isFavorited;

  /// Called with `false` when the user wants to *add* a favorite (was
  /// not favorited) and `true` when they want to *remove* one.
  /// (Reusing the boolean so the parent can keep the toggle state in
  /// sync via [_loadFavorites] after the round-trip.)
  final void Function(bool currentlyFavorited) onToggleFavorite;

  final VoidCallback onSubmitted;

  @override
  State<_AmountForm> createState() => _AmountFormState();
}

class _AmountFormState extends State<_AmountForm> {
  final _formKey = GlobalKey<FormState>();
  final _amountCtrl = TextEditingController();
  late final TextEditingController _mealLabelCtrl;
  String _unit = 'g';
  bool _saveAsFavorite = false;

  bool _saving = false;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    // Pre-fill amount with the food's serving size to make 1-tap logging
    // the common case ("100 g of chicken").
    _amountCtrl.text = widget.food.servingSizeG.toStringAsFixed(0);
    _mealLabelCtrl =
        TextEditingController(text: widget.defaultMealLabel ?? '');
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _mealLabelCtrl.dispose();
    super.dispose();
  }

  // Scaled preview for the user — mirrors the backend's macro scaling rule.
  double _scale(double per100) {
    final amount = double.tryParse(_amountCtrl.text.trim()) ?? 0;
    if (_unit == 'portion') return per100 * amount;
    return amount * per100 / widget.food.servingSizeG;
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _errorText = null;
    });
    final amount = double.parse(_amountCtrl.text.trim());
    try {
      await DietApiService.addDiaryEntry(
        foodId: widget.food.id,
        amount: amount,
        unit: _unit,
        mealLabel: _mealLabelCtrl.text.trim().isEmpty
            ? null
            : _mealLabelCtrl.text.trim(),
      );
      if (_saveAsFavorite && !widget.isFavorited) {
        try {
          await DietApiService.addFavorite(widget.food.id);
        } catch (_) {
          // Best-effort — surfacing a partial failure here would muddy
          // the primary success.
        }
      }
      if (!mounted) return;
      widget.onSubmitted();
    } on ApiException catch (ex) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = ex.message ?? ex.code ?? 'Couldn\'t save entry.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _errorText = 'Couldn\'t save entry.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Per ${widget.food.servingSizeG.toStringAsFixed(0)} g: '
              '${widget.food.kcal.toStringAsFixed(0)} kcal · '
              'P ${widget.food.proteinG.toStringAsFixed(1)} · '
              'C ${widget.food.carbG.toStringAsFixed(1)} · '
              'F ${widget.food.fatG.toStringAsFixed(1)}',
              style: tt.bodySmall,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    controller: _amountCtrl,
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
                    ],
                    decoration: const InputDecoration(
                      labelText: 'Amount',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (_) => setState(() {}),
                    validator: (v) {
                      final t = v?.trim() ?? '';
                      if (t.isEmpty) return 'Required';
                      final n = double.tryParse(t);
                      if (n == null) return 'Invalid number';
                      if (n <= 0) return 'Must be > 0';
                      return null;
                    },
                  ),
                ),
                const SizedBox(width: 12),
                SizedBox(
                  width: 130,
                  child: DropdownButtonFormField<String>(
                    initialValue: _unit,
                    items: const [
                      DropdownMenuItem(value: 'g', child: Text('g')),
                      DropdownMenuItem(value: 'ml', child: Text('ml')),
                      DropdownMenuItem(value: 'portion', child: Text('portion')),
                    ],
                    decoration: const InputDecoration(
                      labelText: 'Unit',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (v) => setState(() => _unit = v ?? 'g'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _mealLabelCtrl,
              maxLength: 60,
              decoration: const InputDecoration(
                labelText: 'Meal label (optional)',
                hintText: 'e.g. Breakfast, Pre-workout',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 4),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: cs.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Adds: ${_scale(widget.food.kcal).toStringAsFixed(0)} kcal · '
                      'P ${_scale(widget.food.proteinG).toStringAsFixed(1)} · '
                      'C ${_scale(widget.food.carbG).toStringAsFixed(1)} · '
                      'F ${_scale(widget.food.fatG).toStringAsFixed(1)}',
                      style: tt.bodyMedium,
                    ),
                  ),
                ],
              ),
            ),
            if (!widget.isFavorited) ...[
              const SizedBox(height: 4),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: _saveAsFavorite,
                onChanged: (v) => setState(() => _saveAsFavorite = v ?? false),
                title: const Text('Also save as favorite for Quick-Add'),
                controlAffinity: ListTileControlAffinity.leading,
                dense: true,
              ),
            ],
            if (_errorText != null) ...[
              const SizedBox(height: 8),
              Text(_errorText!, style: TextStyle(color: cs.error)),
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
                  : const Text('Add to diary'),
            ),
          ],
        ),
      ),
    );
  }
}
