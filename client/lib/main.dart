import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'i18n/app_localizations.dart';
import 'screens/login_screen.dart';
import 'screens/main_shell.dart';
import 'services/api/http_interceptor.dart';
import 'services/secure_storage_service.dart';
import 'styles/app_theme.dart';

/// Used by the http interceptor to bounce the user back to the login screen
/// when the refresh-token path collapses.
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await dotenv.load(fileName: '.env');
  } catch (_) {
    // Defaults in AppConfig will apply.
  }

  // When a request's refresh attempt fails (revoked / expired / reused), the
  // interceptor wipes local auth state and calls this so the UI bails out.
  HttpInterceptor.onUnauthorized = () {
    navigatorKey.currentState?.pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  };

  runApp(const AthleteHubApp());
}

class AthleteHubApp extends StatelessWidget {
  const AthleteHubApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AthleteHub',
      debugShowCheckedModeBanner: false,
      navigatorKey: navigatorKey,
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
      home: const _AuthGate(),
    );
  }
}

/// On launch: check secure storage; route to the main shell if a session
/// exists, otherwise to login. Renders a minimal splash while the async check
/// resolves so the user doesn't see a flash of the login screen on warm starts.
class _AuthGate extends StatefulWidget {
  const _AuthGate();

  @override
  State<_AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<_AuthGate> {
  late final Future<bool> _hasSessionFuture;

  @override
  void initState() {
    super.initState();
    _hasSessionFuture = SecureStorageService.hasSession();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<bool>(
      future: _hasSessionFuture,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }
        return snap.data == true ? const MainShell() : const LoginScreen();
      },
    );
  }
}
