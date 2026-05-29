import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/responses/macros.dart';

/// Three concentric arcs showing protein / carb / fat progress against a
/// target — or, when no target is set, the raw consumed grams next to a
/// neutral idle ring. The kcal number sits in the centre, big.
///
/// Each arc fills proportionally to {@code totals.macro / target.macro},
/// clamped to [0, 1] — going over target shows a full ring (the
/// "remaining" chip below shows the overshoot).
class MacroRing extends StatelessWidget {
  const MacroRing({
    super.key,
    required this.totals,
    this.target,
    this.size = 200,
  });

  final Macros totals;
  final Macros? target;
  final double size;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final kcal = totals.kcal ?? 0;
    final kcalTarget = target?.kcal;

    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size(size, size),
            painter: _MacroRingPainter(
              consumed: totals,
              target: target,
              proteinColor: const Color(0xFF6BB37C),
              carbColor: const Color(0xFFE0A24B),
              fatColor: const Color(0xFFD9627B),
              trackColor: cs.surfaceContainerHighest,
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                kcal.toStringAsFixed(0),
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
              ),
              Text(
                kcalTarget == null
                    ? 'kcal'
                    : 'of ${kcalTarget.toStringAsFixed(0)} kcal',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MacroRingPainter extends CustomPainter {
  _MacroRingPainter({
    required this.consumed,
    required this.target,
    required this.proteinColor,
    required this.carbColor,
    required this.fatColor,
    required this.trackColor,
  });

  final Macros consumed;
  final Macros? target;
  final Color proteinColor;
  final Color carbColor;
  final Color fatColor;
  final Color trackColor;

  static const double _strokeWidth = 8;
  static const double _gap = 6;

  @override
  void paint(Canvas canvas, Size size) {
    final centre = Offset(size.width / 2, size.height / 2);
    // Outer ring at radius (smaller side / 2 - stroke / 2 - 2) — leaves
    // room for the gap between arcs.
    final outer = math.min(size.width, size.height) / 2 - _strokeWidth / 2;
    final radiusP = outer;
    final radiusC = outer - (_strokeWidth + _gap);
    final radiusF = outer - 2 * (_strokeWidth + _gap);

    _paintArc(canvas, centre, radiusP,
        _ratio(consumed.proteinG, target?.proteinG), proteinColor);
    _paintArc(canvas, centre, radiusC,
        _ratio(consumed.carbG, target?.carbG), carbColor);
    _paintArc(canvas, centre, radiusF,
        _ratio(consumed.fatG, target?.fatG), fatColor);
  }

  /// When no target → 1.0 (idle ring fills to show the colour and current
  /// value below). When target is 0 → also 1.0. Otherwise clamped to
  /// [0, 1] — over-target lights the full ring; the overshoot is in the
  /// "remaining" chip.
  double _ratio(double? consumedG, double? targetG) {
    if (targetG == null || targetG <= 0) return consumedG == null ? 0 : 1;
    final r = (consumedG ?? 0) / targetG;
    return r.clamp(0.0, 1.0);
  }

  void _paintArc(Canvas canvas, Offset centre, double radius,
      double ratio, Color color) {
    final rect = Rect.fromCircle(center: centre, radius: radius);
    final track = Paint()
      ..color = trackColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth;
    final fg = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(rect, 0, math.pi * 2, false, track);
    if (ratio > 0) {
      // Start at 12 o'clock (-pi/2) and sweep clockwise.
      canvas.drawArc(rect, -math.pi / 2, math.pi * 2 * ratio, false, fg);
    }
  }

  @override
  bool shouldRepaint(_MacroRingPainter old) =>
      old.consumed != consumed || old.target != target;
}
