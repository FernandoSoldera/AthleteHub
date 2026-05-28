import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'i18n/app_localizations.dart';
import 'screens/placeholder_screen.dart';
import 'styles/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Optional .env — don't crash if it's missing (e.g. fresh checkout).
  try {
    await dotenv.load(fileName: '.env');
  } catch (_) {
    // Defaults in AppConfig will apply.
  }
  runApp(const AthleteHubApp());
}

class AthleteHubApp extends StatelessWidget {
  const AthleteHubApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AthleteHub',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.themeFor(brightness: Brightness.light),
      darkTheme: AppTheme.themeFor(brightness: Brightness.dark),
      themeMode: ThemeMode.dark, // Design is dark-first; settings will expose this later.
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en'), Locale('pt')],
      home: const MainShell(),
    );
  }
}

/// Athlete app shell with five bottom tabs. Coach mode (different tab set)
/// arrives in EPIC 7 (AH-075). Screens are placeholders until their epic lands.
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

    final tabIcons = [
      Icons.dynamic_feed_outlined,
      Icons.fitness_center_outlined,
      Icons.show_chart_outlined,
      Icons.restaurant_outlined,
      Icons.person_outline,
    ];

    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          for (var i = 0; i < tabs.length; i++)
            PlaceholderScreen(title: tabs[i].label, icon: tabIcons[i]),
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
