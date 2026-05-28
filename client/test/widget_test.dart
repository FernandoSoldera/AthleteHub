// Widget smoke tests for the login screen.
//
// We test LoginScreen in isolation rather than booting the whole app via
// `main()` — the auth gate calls native flutter_secure_storage, which isn't
// wired in the unit-test environment. Integration testing the boot flow lives
// in integration_test/ (real device or emulator) instead.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:athletehub/screens/login_screen.dart';

Widget _harness(Widget child) {
  return MaterialApp(home: child);
}

void main() {
  testWidgets('login screen renders sign-in mode with email + password fields',
      (tester) async {
    await tester.pumpWidget(_harness(const LoginScreen()));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    // "Sign in" appears on the segmented control + the primary button.
    expect(find.text('Sign in'), findsAtLeastNWidgets(1));
    expect(find.text('Create account'), findsAtLeastNWidgets(1));
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('Full name'), findsNothing);
  });

  testWidgets('toggling to create-account mode reveals full name + handle',
      (tester) async {
    await tester.pumpWidget(_harness(const LoginScreen()));
    await tester.pumpAndSettle();

    // Tap the "Create account" segment by widget type to avoid duplicate-text
    // matches with the primary button.
    final segment = find
        .descendant(
          of: find.byType(SegmentedButton<bool>),
          matching: find.text('Create account'),
        )
        .first;
    await tester.tap(segment);
    await tester.pumpAndSettle();

    expect(find.text('Full name'), findsOneWidget);
    expect(find.text('Handle (e.g. alex.lifts)'), findsOneWidget);
  });
}
