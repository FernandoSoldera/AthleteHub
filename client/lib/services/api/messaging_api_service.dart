import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/conversation.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/message.dart';
import 'http_interceptor.dart';

/// Messaging API — inbox list, thread paging, send, mark-read, plus a
/// lazy open-for-relationship endpoint so the client can jump straight
/// into the (auto-created) chat with a coach or athlete without tracking
/// the conversation id beforehand.
class MessagingApiService {
  MessagingApiService._();

  // ── inbox + thread ─────────────────────────────────────────────────────

  static Future<CursorPage<Conversation>> inbox({
    String? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get('/api/conversations?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<Conversation>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Conversation.fromJson,
    );
  }

  static Future<CursorPage<Message>> messages({
    required int conversationId,
    String? cursor,
    int limit = 30,
  }) async {
    final qs = _qs({'cursor': cursor, 'limit': '$limit'});
    final response = await HttpInterceptor.get(
        '/api/conversations/$conversationId/messages?$qs');
    _ensureOk(response);
    return CursorPage.fromJson<Message>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Message.fromJson,
    );
  }

  // ── send + read ────────────────────────────────────────────────────────

  static Future<Message> sendMessage({
    required int conversationId,
    required String body,
  }) async {
    final response = await HttpInterceptor.post(
      '/api/conversations/$conversationId/messages',
      body: {'body': body},
    );
    _ensureStatus(response, const [200, 201]);
    return Message.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> markRead(int conversationId) async {
    final response = await HttpInterceptor.post(
        '/api/conversations/$conversationId/read');
    _ensureStatus(response, const [204]);
  }

  // ── lazy open ──────────────────────────────────────────────────────────

  /// Returns the conversation tied to a coach-athlete relationship — created
  /// on first call. Used by "open chat with my coach / this athlete" links.
  static Future<Conversation> openForRelationship(int relationshipId) async {
    final response = await HttpInterceptor.post(
        '/api/me/coach-athletes/$relationshipId/conversation');
    _ensureOk(response);
    return Conversation.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  static String _qs(Map<String, String?> params) {
    final parts = <String>[];
    params.forEach((k, v) {
      if (v == null || v.isEmpty) return;
      parts.add('${Uri.encodeQueryComponent(k)}=${Uri.encodeQueryComponent(v)}');
    });
    return parts.join('&');
  }

  static void _ensureOk(http.Response response) =>
      _ensureStatus(response, const [200]);

  static void _ensureStatus(http.Response response, List<int> allowed) {
    if (allowed.contains(response.statusCode)) return;
    ApiErrorResponse? err;
    try {
      final body = jsonDecode(response.body);
      if (body is Map<String, dynamic>) {
        err = ApiErrorResponse.fromJson(response.statusCode, body);
      }
    } catch (_) {}
    throw ApiException(
      statusCode: response.statusCode,
      code: err?.code,
      message: err?.message,
      fieldErrors: err?.fieldErrors,
    );
  }
}
