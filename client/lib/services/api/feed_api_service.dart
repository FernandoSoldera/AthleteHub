import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../models/responses/api_error_response.dart';
import '../../models/responses/comment.dart';
import '../../models/responses/cursor_page.dart';
import '../../models/responses/feed_item.dart';
import '../../models/responses/post.dart';
import 'http_interceptor.dart';

/// Feed + interaction API (AH-061/062/063). All endpoints route through
/// [HttpInterceptor] so 401 → silent refresh + retry happens transparently.
class FeedApiService {
  FeedApiService._();

  // ── feed reads ────────────────────────────────────────────────────────

  /// Home timeline — caller's own posts plus their followees' public /
  /// followers posts. Optional comma-separated type filter
  /// (e.g. `workout,run`).
  static Future<CursorPage<FeedItem>> homeFeed({
    int? cursor,
    int limit = 20,
    String? type,
  }) async {
    final qs = _qs({
      'cursor': cursor?.toString(),
      'limit': '$limit',
      'type': type,
    });
    final url = qs.isEmpty ? '/api/feed' : '/api/feed?$qs';
    final response = await HttpInterceptor.get(url);
    _ensureOk(response);
    return CursorPage.fromJson<FeedItem>(
      jsonDecode(response.body) as Map<String, dynamic>,
      FeedItem.fromJson,
    );
  }

  /// Profile feed for one author — visibility derived server-side from
  /// the viewer-author relationship (self → all; follower → public +
  /// followers; stranger → public only).
  static Future<CursorPage<FeedItem>> profileFeed(
    String handle, {
    int? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final url = qs.isEmpty
        ? '/api/users/$handle/posts'
        : '/api/users/$handle/posts?$qs';
    final response = await HttpInterceptor.get(url);
    _ensureOk(response);
    return CursorPage.fromJson<FeedItem>(
      jsonDecode(response.body) as Map<String, dynamic>,
      FeedItem.fromJson,
    );
  }

  // ── manual post create / delete ───────────────────────────────────────

  static Future<Post> createManualPost({
    String? title,
    String? note,
    String? visibility,
  }) async {
    final body = <String, dynamic>{};
    if (title != null && title.isNotEmpty) body['title'] = title;
    if (note != null && note.isNotEmpty) body['note'] = note;
    if (visibility != null) body['visibility'] = visibility;
    final response = await HttpInterceptor.post('/api/posts', body: body);
    _ensureStatus(response, const [201]);
    return Post.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> deletePost(int postId) async {
    final response = await HttpInterceptor.delete('/api/posts/$postId');
    _ensureStatus(response, const [204]);
  }

  // ── likes (idempotent) ────────────────────────────────────────────────

  static Future<void> like(int postId) async {
    final response = await HttpInterceptor.post('/api/posts/$postId/likes');
    _ensureStatus(response, const [204]);
  }

  static Future<void> unlike(int postId) async {
    final response = await HttpInterceptor.delete('/api/posts/$postId/likes');
    _ensureStatus(response, const [204]);
  }

  // ── comments ─────────────────────────────────────────────────────────

  static Future<CursorPage<Comment>> listComments(
    int postId, {
    int? cursor,
    int limit = 20,
  }) async {
    final qs = _qs({'cursor': cursor?.toString(), 'limit': '$limit'});
    final url = qs.isEmpty
        ? '/api/posts/$postId/comments'
        : '/api/posts/$postId/comments?$qs';
    final response = await HttpInterceptor.get(url);
    _ensureOk(response);
    return CursorPage.fromJson<Comment>(
      jsonDecode(response.body) as Map<String, dynamic>,
      Comment.fromJson,
    );
  }

  static Future<Comment> addComment(int postId, String body) async {
    final response = await HttpInterceptor.post(
      '/api/posts/$postId/comments',
      body: {'body': body},
    );
    _ensureStatus(response, const [201]);
    return Comment.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  static Future<void> deleteComment(int commentId) async {
    final response = await HttpInterceptor.delete('/api/comments/$commentId');
    _ensureStatus(response, const [204]);
  }

  // ── helpers ───────────────────────────────────────────────────────────

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
