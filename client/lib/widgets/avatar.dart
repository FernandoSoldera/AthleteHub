import 'package:flutter/material.dart';

/// Initial-based avatar tinted by the design's `avatarHue` (HSL hue 0-359).
/// The fallback hue keeps anonymous users visually grounded.
class Avatar extends StatelessWidget {
  const Avatar({
    super.key,
    required this.fullName,
    this.hue,
    this.size = 44,
  });

  final String fullName;
  final int? hue;
  final double size;

  String get _initials {
    final parts = fullName.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      final s = parts.first;
      return s.isEmpty ? '?' : s.substring(0, 1).toUpperCase();
    }
    return (parts.first.substring(0, 1) + parts.last.substring(0, 1)).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final h = (hue ?? 220).toDouble().clamp(0.0, 359.0);
    final bg = HSLColor.fromAHSL(1, h, 0.55, 0.45).toColor();
    final fg = HSLColor.fromAHSL(1, h, 0.5, 0.92).toColor();
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: bg,
        shape: BoxShape.circle,
      ),
      child: Text(
        _initials,
        style: TextStyle(
          color: fg,
          fontWeight: FontWeight.w700,
          fontSize: size * 0.36,
          letterSpacing: -0.2,
        ),
      ),
    );
  }
}
