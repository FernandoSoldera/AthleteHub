import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/auth_api_service.dart';
import 'forgot_password_screen.dart';
import 'main_shell.dart';

/// Combined sign-in / create-account screen matching the original
/// `SignInScreen` from the handoff: a segmented control flips between the
/// two modes, social buttons sit under an "or continue with" divider, and
/// the form fields shift accordingly.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _fullNameController = TextEditingController();
  final _handleController = TextEditingController();

  bool _signUp = false;
  bool _submitting = false;
  String? _errorText;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _fullNameController.dispose();
    _handleController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _errorText = null;
    });
    try {
      if (_signUp) {
        await AuthApiService.register(
          email: _emailController.text.trim(),
          password: _passwordController.text,
          fullName: _fullNameController.text.trim(),
          handle: _handleController.text.trim(),
        );
      }
      await AuthApiService.login(
        email: _emailController.text.trim(),
        password: _passwordController.text,
      );
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const MainShell()),
        (_) => false,
      );
    } on ApiException catch (ex) {
      setState(() => _errorText = _humanize(ex));
    } catch (_) {
      setState(() => _errorText = 'Something went wrong. Please try again.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _humanize(ApiException ex) {
    switch (ex.code) {
      case 'INVALID_CREDENTIALS':
        return 'Invalid email or password.';
      case 'EMAIL_ALREADY_REGISTERED':
        return 'That email is already registered.';
      case 'HANDLE_ALREADY_TAKEN':
        return 'That handle is taken — try another.';
      case 'VALIDATION_FAILED':
        if (ex.fieldErrors != null && ex.fieldErrors!.isNotEmpty) {
          return ex.fieldErrors!.values.first;
        }
        return 'Some fields are invalid.';
      default:
        return ex.message ?? 'Something went wrong. Please try again.';
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Form(
            key: _formKey,
            child: ListView(
              children: [
                const SizedBox(height: 48),
                _Logo(color: cs.primary, foreground: cs.onPrimary),
                const SizedBox(height: 28),
                RichText(
                  text: TextSpan(
                    style: tt.headlineLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                          height: 1.05,
                        ) ??
                        const TextStyle(
                            fontSize: 36, fontWeight: FontWeight.w800),
                    children: [
                      const TextSpan(text: 'Train. Track.\n'),
                      TextSpan(
                        text: 'Evolve.',
                        style: TextStyle(color: cs.primary),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'The training journal that turns reps into receipts.',
                  style: tt.bodyMedium,
                ),
                const SizedBox(height: 28),
                _Segmented(
                  signUp: _signUp,
                  onChanged: (signUp) {
                    setState(() {
                      _signUp = signUp;
                      _errorText = null;
                    });
                  },
                ),
                const SizedBox(height: 12),
                if (_signUp) ...[
                  _TextField(
                    controller: _fullNameController,
                    label: 'Full name',
                    icon: Icons.person_outline,
                    validator: _requiredValidator,
                    autofillHints: const [AutofillHints.name],
                  ),
                  const SizedBox(height: 10),
                  _TextField(
                    controller: _handleController,
                    label: 'Handle (e.g. alex.lifts)',
                    icon: Icons.alternate_email,
                    validator: _handleValidator,
                  ),
                  const SizedBox(height: 10),
                ],
                _TextField(
                  controller: _emailController,
                  label: 'Email',
                  icon: Icons.mail_outline,
                  keyboardType: TextInputType.emailAddress,
                  validator: _emailValidator,
                  autofillHints: const [AutofillHints.email],
                ),
                const SizedBox(height: 10),
                _TextField(
                  controller: _passwordController,
                  label: 'Password',
                  icon: Icons.lock_outline,
                  obscure: true,
                  validator: _passwordValidator,
                  autofillHints: _signUp
                      ? const [AutofillHints.newPassword]
                      : const [AutofillHints.password],
                ),
                if (!_signUp)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(
                            builder: (_) => const ForgotPasswordScreen()),
                      ),
                      child: const Text('Forgot password?'),
                    ),
                  ),
                if (_errorText != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    _errorText!,
                    style: tt.bodySmall?.copyWith(color: cs.error),
                  ),
                ],
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _submitting ? null : _submit,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                    backgroundColor: cs.primary,
                    foregroundColor: cs.onPrimary,
                  ),
                  child: _submitting
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(_signUp ? 'Create account' : 'Sign in'),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    const Expanded(child: Divider()),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      child: Text('or continue with',
                          style: tt.bodySmall),
                    ),
                    const Expanded(child: Divider()),
                  ],
                ),
                const SizedBox(height: 12),
                // OAuth buttons are stubs until the native flow lands.
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: null,
                        icon: const Icon(Icons.apple, size: 18),
                        label: const Text('Apple'),
                        style: OutlinedButton.styleFrom(
                            minimumSize: const Size.fromHeight(48)),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: null,
                        icon: const Icon(Icons.g_mobiledata, size: 24),
                        label: const Text('Google'),
                        style: OutlinedButton.styleFrom(
                            minimumSize: const Size.fromHeight(48)),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Center(
                  child: Text(
                    'By continuing you agree to our Terms & Privacy.',
                    textAlign: TextAlign.center,
                    style: tt.bodySmall,
                  ),
                ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ── validators ─────────────────────────────────────────────────────────

  String? _requiredValidator(String? value) {
    if (value == null || value.trim().isEmpty) return 'Required';
    return null;
  }

  String? _emailValidator(String? value) {
    final v = value?.trim() ?? '';
    if (v.isEmpty) return 'Required';
    if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(v)) {
      return 'Enter a valid email';
    }
    return null;
  }

  String? _passwordValidator(String? value) {
    if (value == null || value.isEmpty) return 'Required';
    if (_signUp && value.length < 8) return 'At least 8 characters';
    return null;
  }

  String? _handleValidator(String? value) {
    final v = value?.trim() ?? '';
    if (v.isEmpty) return 'Required';
    if (v.length < 3) return 'At least 3 characters';
    if (!RegExp(r'^[a-zA-Z0-9_.]+$').hasMatch(v)) {
      return 'Letters, digits, dot or underscore only';
    }
    return null;
  }
}

// ── small private widgets ─────────────────────────────────────────────────

class _Logo extends StatelessWidget {
  const _Logo({required this.color, required this.foreground});
  final Color color;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 64,
      height: 64,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [color, color.withValues(alpha: 0.6)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(18),
        boxShadow: [
          BoxShadow(
            color: color.withValues(alpha: 0.35),
            blurRadius: 24,
            offset: const Offset(0, 0),
          ),
        ],
      ),
      child: Icon(Icons.bolt_rounded, color: foreground, size: 36),
    );
  }
}

class _Segmented extends StatelessWidget {
  const _Segmented({required this.signUp, required this.onChanged});
  final bool signUp;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<bool>(
      segments: const [
        ButtonSegment(value: false, label: Text('Sign in')),
        ButtonSegment(value: true, label: Text('Create account')),
      ],
      selected: {signUp},
      showSelectedIcon: false,
      onSelectionChanged: (s) => onChanged(s.first),
    );
  }
}

class _TextField extends StatelessWidget {
  const _TextField({
    required this.controller,
    required this.label,
    this.icon,
    this.obscure = false,
    this.keyboardType,
    this.validator,
    this.autofillHints,
  });

  final TextEditingController controller;
  final String label;
  final IconData? icon;
  final bool obscure;
  final TextInputType? keyboardType;
  final String? Function(String?)? validator;
  final Iterable<String>? autofillHints;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      obscureText: obscure,
      keyboardType: keyboardType,
      autofillHints: autofillHints,
      validator: validator,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: icon == null ? null : Icon(icon, size: 18),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
        ),
      ),
    );
  }
}
