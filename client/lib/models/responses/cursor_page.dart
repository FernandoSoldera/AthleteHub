/// Generic envelope for cursor-paginated list responses
/// `{ items: [...], nextCursor: "..." | null }`.
class CursorPage<T> {
  CursorPage({required this.items, this.nextCursor});

  final List<T> items;
  final String? nextCursor;

  bool get hasMore => nextCursor != null;

  static CursorPage<T> fromJson<T>(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) itemFromJson,
  ) {
    final raw = (json['items'] as List<dynamic>? ?? const []);
    return CursorPage<T>(
      items: raw.map((e) => itemFromJson(e as Map<String, dynamic>)).toList(),
      nextCursor: json['nextCursor'] as String?,
    );
  }
}
