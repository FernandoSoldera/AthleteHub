import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Loads translations from `assets/i18n/<lang>.json` and exposes them as typed
/// getters. Mirrors lotuga's pattern: nested JSON addressed by dot-paths
/// (`login.title`), with `translateErrorCode` / `translateSuccessCode` mapping
/// backend [MessageCode] strings to localized text.
class AppLocalizations {
  AppLocalizations(this.locale);

  final Locale locale;
  Map<String, dynamic> _strings = const {};

  static AppLocalizations of(BuildContext context) =>
      Localizations.of<AppLocalizations>(context, AppLocalizations)!;

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  Future<bool> load() async {
    final raw = await rootBundle
        .loadString('assets/i18n/${locale.languageCode}.json');
    _strings = json.decode(raw) as Map<String, dynamic>;
    return true;
  }

  String _t(String key) {
    dynamic value = _strings;
    for (final segment in key.split('.')) {
      if (value is Map && value.containsKey(segment)) {
        value = value[segment];
      } else {
        return key; // missing — surface the key so it's easy to spot
      }
    }
    return value.toString();
  }

  // App
  String get appName => _t('app.name');

  // Common
  String get commonEmail => _t('common.email');
  String get commonPassword => _t('common.password');
  String get commonOr => _t('common.or');
  String get commonCancel => _t('common.cancel');
  String get commonSave => _t('common.save');

  // Navigation (athlete tabs — coach tabs added in EPIC 7)
  String get navFeed => _t('navigation.feed');
  String get navTrain => _t('navigation.train');
  String get navEvolve => _t('navigation.evolve');
  String get navDiet => _t('navigation.diet');
  String get navMe => _t('navigation.me');

  /// Map a backend [MessageCode] (string) to a localized error message.
  String translateErrorCode(String? code) {
    if (code == null || code.isEmpty) return _t('errors.UNKNOWN_ERROR');
    final translated = _t('errors.$code');
    return translated == 'errors.$code' ? _t('errors.UNKNOWN_ERROR') : translated;
  }
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) =>
      const ['en', 'pt'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async {
    final loc = AppLocalizations(locale);
    await loc.load();
    return loc;
  }

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}
