import 'package:flutter/material.dart';

/// The four design accents from the original tokens (`tokens.css`).
enum AccentPalette { volt, cyan, magenta, orange }

/// Colors and ThemeData for AthleteHub. Builds a Material 3 [ThemeData] for
/// any combination of [Brightness] + [AccentPalette]. Source-of-truth values
/// come straight from the design tokens; status colors (hot/cold/warn) are
/// approximations of the oklch tokens in plain RGB hex.
class AppTheme {
  AppTheme._();

  // ── Accent palettes (from tokens.css → hex from the design swatches) ──
  static const Color voltAccent = Color(0xFFD4FF3A);
  static const Color voltOnAccent = Color(0xFF0A0E08);

  static const Color cyanAccent = Color(0xFF00E5FF);
  static const Color cyanOnAccent = Color(0xFF03161A);

  static const Color magentaAccent = Color(0xFFFF48A5);
  static const Color magentaOnAccent = Color(0xFF1A0510);

  static const Color orangeAccent = Color(0xFFFF8A3A);
  static const Color orangeOnAccent = Color(0xFF1A0A02);

  // ── Status colors (shared across themes) ──
  static const Color hot = Color(0xFFFF7A5C);   // oklch(0.72 0.21 30)
  static const Color cold = Color(0xFF74C0E8);  // oklch(0.78 0.14 220)
  static const Color warn = Color(0xFFF0C75C);  // oklch(0.82 0.16 70)

  // ── Dark surfaces ──
  static const Color darkBg = Color(0xFF06080B);
  static const Color darkSurface = Color(0xFF0E1319);
  static const Color darkSurface2 = Color(0xFF141A23);
  static const Color darkSurface3 = Color(0xFF1C2330);
  static const Color darkText = Color(0xFFF3F5F9);
  static const Color darkText2 = Color(0xFFA8B0BF);
  static const Color darkText3 = Color(0xFF6B7384);

  // ── Light surfaces ──
  static const Color lightBg = Color(0xFFF3F4F7);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurface2 = Color(0xFFF6F7FA);
  static const Color lightSurface3 = Color(0xFFEDEFF3);
  static const Color lightText = Color(0xFF0A0E16);
  static const Color lightText2 = Color(0xFF4C5363);
  static const Color lightText3 = Color(0xFF7A8090);

  static (Color, Color) _accentColors(AccentPalette accent) {
    return switch (accent) {
      AccentPalette.volt => (voltAccent, voltOnAccent),
      AccentPalette.cyan => (cyanAccent, cyanOnAccent),
      AccentPalette.magenta => (magentaAccent, magentaOnAccent),
      AccentPalette.orange => (orangeAccent, orangeOnAccent),
    };
  }

  /// Build a ThemeData for the given [brightness] and [accent].
  static ThemeData themeFor({
    required Brightness brightness,
    AccentPalette accent = AccentPalette.volt,
  }) {
    final (accentColor, onAccent) = _accentColors(accent);
    final isDark = brightness == Brightness.dark;

    final colorScheme = ColorScheme(
      brightness: brightness,
      primary: accentColor,
      onPrimary: onAccent,
      secondary: accentColor,
      onSecondary: onAccent,
      surface: isDark ? darkSurface : lightSurface,
      onSurface: isDark ? darkText : lightText,
      error: hot,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: isDark ? darkBg : lightBg,
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: isDark ? darkSurface : lightSurface,
        indicatorColor: accentColor.withValues(alpha: 0.16),
        labelTextStyle: WidgetStatePropertyAll(
          TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            color: isDark ? darkText2 : lightText2,
          ),
        ),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected
                ? accentColor
                : (isDark ? darkText3 : lightText3),
            size: 22,
          );
        }),
      ),
      textTheme: TextTheme(
        bodyMedium: TextStyle(color: isDark ? darkText : lightText),
        bodySmall: TextStyle(color: isDark ? darkText2 : lightText2),
      ),
    );
  }
}
