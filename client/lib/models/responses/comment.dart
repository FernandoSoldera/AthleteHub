import '../public_user.dart';

/// One comment on a post + its hydrated author. Mirrors backend
/// `CommentDto`. Soft-deleted comments aren't returned by the thread
/// endpoint, so the wire shape only carries active rows.
class Comment {
  Comment({
    required this.id,
    required this.postId,
    required this.body,
    required this.createdAt,
    this.author,
  });

  final int id;
  final int postId;
  final String body;
  final DateTime createdAt;
  final PublicUser? author;

  factory Comment.fromJson(Map<String, dynamic> json) {
    return Comment(
      id: (json['id'] as num).toInt(),
      postId: (json['postId'] as num).toInt(),
      body: json['body'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      author: json['author'] == null
          ? null
          : PublicUser.fromJson(json['author'] as Map<String, dynamic>),
    );
  }
}
