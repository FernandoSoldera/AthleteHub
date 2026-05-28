import 'package:flutter/material.dart';

import '../models/responses/api_error_response.dart';
import '../services/api/auth_api_service.dart';
import 'reset_password_screen.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _submitting = false;
  String? _errorText;

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _errorText = null;
    });
    try {
      await AuthApiService.forgotPassword(_emailController.text.trim());
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const ResetPasswordScreen()),
      );
    } on ApiException catch (ex) {
      setState(() => _errorText = ex.message ?? 'Something went wrong.');
    } catch (_) {
      setState(() => _errorText = 'Something went wrong.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Forgot password')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Form(
            key: _formKey,
            child: ListView(
              children: [
                const SizedBox(height: 12),
                Text(
                  "Enter the email you signed up with and we'll send you a 6-character reset code.",
                  style: tt.bodyMedium,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _emailController,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  decoration: InputDecoration(
                    labelText: 'Email',
                    prefixIcon: const Icon(Icons.mail_outline, size: 18),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  validator: (v) {
                    final s = v?.trim() ?? '';
                    if (s.isEmpty) return 'Required';
                    if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(s)) {
                      return 'Enter a valid email';
                    }
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
                  onPressed: _submitting ? null : _send,
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
                      : const Text('Send code'),
                ),
                const SizedBox(height: 8),
                Center(
                  child: TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Back to sign in'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
