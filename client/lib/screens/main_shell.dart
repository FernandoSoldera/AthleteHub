import 'package:flutter/material.dart';

import '../i18n/app_localizations.dart';
import 'placeholder_screen.dart';

/// Athlete app shell with five bottom tabs. Coach mode (a different tab set)
/// arrives in EPIC 7 (AH-075). Screens are placeholders until their epic
/// lands. Extracted from `main.dart` so the login flow can navigate to it.
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);

    final tabs = <_Tab>[
      _Tab(l.navFeed, Icons.dynamic_feed_outlined, Icons.dynamic_feed),
      _Tab(l.navTrain, Icons.fitness_center_outlined, Icons.fitness_center),
      _Tab(l.navEvolve, Icons.show_chart_outlined, Icons.show_chart),
      _Tab(l.navDiet, Icons.restaurant_outlined, Icons.restaurant),
      _Tab(l.navMe, Icons.person_outline, Icons.person),
    ];

    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          for (final t in tabs) PlaceholderScreen(title: t.label, icon: t.icon),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: [
          for (final t in tabs)
            NavigationDestination(
              icon: Icon(t.icon),
              selectedIcon: Icon(t.selectedIcon),
              label: t.label,
            ),
        ],
      ),
    );
  }
}

class _Tab {
  const _Tab(this.label, this.icon, this.selectedIcon);
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}
