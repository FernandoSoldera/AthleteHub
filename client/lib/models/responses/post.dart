/// One feed post. Mirrors backend `PostDto`. The `payload` JSONB snapshot
/// is the per-type render data captured at publish time — the card reads
/// from this map without dereferencing the soft link to the source row.
class Post {
  Post({
    required this.id,
    required this.authorId,
    required this.type,
    required this.visibility,
    required this.likeCount,
    required this.commentCount,
    required this.createdAt,
    this.title,
    this.note,
    this.sourceRefType,
    this.sourceRefId,
    this.payload,
  });

  final int id;
  final int authorId;

  /// One of {@code workout | run | cycle | evolution | manual}.
  final String type;
  final String? title;
  final String? note;
  final String? sourceRefType;
  final int? sourceRefId;

  /// Per-type snapshot: workout has `totalVolumeKg`/`totalSets`/`prCount`,
  /// run/cycle has `distanceM`/`durationSeconds`/`avgHr?`/`kcal?`,
  /// evolution has `weightKg`/`bodyFatPct?`/`bfMethod?`, manual is null.
  final Map<String, dynamic>? payload;

  /// One of {@code public | followers | private}.
  final String visibility;
  final int likeCount;
  final int commentCount;
  final DateTime createdAt;

  factory Post.fromJson(Map<String, dynamic> json) {
    return Post(
      id: (json['id'] as num).toInt(),
      authorId: (json['authorId'] as num).toInt(),
      type: json['type'] as String,
      title: json['title'] as String?,
      note: json['note'] as String?,
      sourceRefType: json['sourceRefType'] as String?,
      sourceRefId: (json['sourceRefId'] as num?)?.toInt(),
      payload: json['payload'] == null
          ? null
          : Map<String, dynamic>.from(json['payload'] as Map),
      visibility: json['visibility'] as String? ?? 'followers',
      likeCount: (json['likeCount'] as num?)?.toInt() ?? 0,
      commentCount: (json['commentCount'] as num?)?.toInt() ?? 0,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  /// Returns a copy with mutable fields swapped — used by the feed card
  /// for optimistic like-count flips that get reconciled on the API
  /// response.
  Post copyWith({int? likeCount, int? commentCount}) {
    return Post(
      id: id,
      authorId: authorId,
      type: type,
      title: title,
      note: note,
      sourceRefType: sourceRefType,
      sourceRefId: sourceRefId,
      payload: payload,
      visibility: visibility,
      likeCount: likeCount ?? this.likeCount,
      commentCount: commentCount ?? this.commentCount,
      createdAt: createdAt,
    );
  }
}
