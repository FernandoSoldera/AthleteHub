import 'package:flutter/material.dart';

import '../i18n/app_localizations.dart';
import '../services/secure_storage_service.dart';
import 'diet_screen.dart';
import 'evolution_screen.dart';
import 'feed_screen.dart';
import 'placeholder_screen.dart';
import 'profile_screen.dart';
import 'train_screen.dart';

/// Athlete app shell with five bottom tabs. Coach mode (a different tab set)
/// arrives in EPIC 7 (AH-075). Most tabs are still placeholders until their
/// epic lands — Me uses [ProfileScreen] with the cached user's handle (AH-024),
/// the Feed AppBar exposes a search action that opens [FindPeopleScreen].
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _index = 0;
  String? _myHandle;
  bool _resolvingHandle = true;

  @override
  void initState() {
    super.initState();
    _loadMyHandle();
  }

  Future<void> _loadMyHandle() async {
    final me = await SecureStorageService.getCachedUser();
    if (!mounted) return;
    setState(() {
      _myHandle = me?.handle;
      _resolvingHandle = false;
    });
  }

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
          const FeedScreen(),
          const TrainScreen(),
          const EvolutionScreen(),
          const DietScreen(),
          _MeTab(
            fallbackTitle: tabs[4].label,
            icon: tabs[4].icon,
            handle: _myHandle,
            resolving: _resolvingHandle,
          ),
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

/// Me tab — renders the viewer's own [ProfileScreen] once their handle is
/// resolved from secure storage. Falls back to the placeholder if the cached
/// user is missing (shouldn't happen post-login, but keeps the shell safe).
class _MeTab extends StatelessWidget {
  const _MeTab({
    required this.fallbackTitle,
    required this.icon,
    required this.handle,
    required this.resolving,
  });

  final String fallbackTitle;
  final IconData icon;
  final String? handle;
  final bool resolving;

  @override
  Widget build(BuildContext context) {
    if (resolving) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    if (handle == null) {
      return PlaceholderScreen(title: fallbackTitle, icon: icon);
    }
    return ProfileScreen(handle: handle!);
  }
}

class _Tab {
  const _Tab(this.label, this.icon, this.selectedIcon);
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}
