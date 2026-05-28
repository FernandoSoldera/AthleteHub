import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/auth_api_service.dart';
import 'login_screen.dart';

class ResetPasswordScreen extends StatefulWidget {
  const ResetPasswordScreen({super.key});

  @override
  State<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends State<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _codeController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _submitting = false;
  String? _errorText;

  @override
  void dispose() {
    _codeController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _reset() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _errorText = null;
    });
    try {
      await AuthApiService.resetPassword(
        code: _codeController.text.trim().toUpperCase(),
        password: _passwordController.text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Password updated — sign in again.')),
      );
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (_) => false,
      );
    } on ApiException catch (ex) {
      setState(() => _errorText = _humanize(ex));
    } catch (_) {
      setState(() => _errorText = 'Something went wrong.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _humanize(ApiException ex) {
    if (ex.code == 'INVALID_RESET_CODE') {
      return 'That code is invalid or expired.';
    }
    if (ex.code == 'VALIDATION_FAILED' && ex.fieldErrors != null) {
      return ex.fieldErrors!.values.first;
    }
    return ex.message ?? 'Something went wrong.';
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Reset password')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Form(
            key: _formKey,
            child: ListView(
              children: [
                const SizedBox(height: 12),
                Text(
                  'Enter the code we emailed you and choose a new password.',
                  style: tt.bodyMedium,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _codeController,
                  keyboardType: TextInputType.visiblePassword,
                  textCapitalization: TextCapitalization.characters,
                  maxLength: 6,
                  decoration: InputDecoration(
                    labelText: 'Code (6 characters)',
                    prefixIcon: const Icon(Icons.confirmation_number_outlined,
                        size: 18),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  validator: (v) {
                    final s = (v ?? '').trim().toUpperCase();
                    if (s.isEmpty) return 'Required';
                    if (!RegExp(r'^[0-9A-F]{6}$').hasMatch(s)) {
                      return 'Six characters, 0-9 and A-F only';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _passwordController,
                  obscureText: true,
                  autofillHints: const [AutofillHints.newPassword],
                  decoration: InputDecoration(
                    labelText: 'New password',
                    prefixIcon: const Icon(Icons.lock_outline, size: 18),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) return 'Required';
                    if (v.length < 8) return 'At least 8 characters';
                    return null;
                  },
                ),
                if (_errorText != null) ...[
                  const SizedBox(height: 8),
                  Text(_errorText!,
                      style: tt.bodySmall?.copyWith(color: cs.error)),
                ],
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _submitting ? null : _reset,
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
                      : const Text('Reset password'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
