// Smoke test: the app shell builds and shows the five athlete tabs.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:athletehub/main.dart';

void main() {
  testWidgets('app shell renders the athlete navigation bar', (tester) async {
    await tester.pumpWidget(const AthleteHubApp());
    // Allow async localization delegate to load.
    await tester.pumpAndSettle();

    expect(find.byType(NavigationBar), findsOneWidget);
    // 5 destinations
    expect(find.byType(NavigationDestination), findsNWidgets(5));
  });
}
