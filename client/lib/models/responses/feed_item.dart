import '../public_user.dart';
import 'post.dart';

/// One item on a feed page — post + hydrated author + viewer-scoped
/// `iLiked` flag. Mirrors backend `FeedItemDto`.
class FeedItem {
  FeedItem({required this.post, required this.iLiked, this.author});

  Post post;
  final PublicUser? author;
  bool iLiked;

  factory FeedItem.fromJson(Map<String, dynamic> json) {
    return FeedItem(
      post: Post.fromJson(json['post'] as Map<String, dynamic>),
      author: json['author'] == null
          ? null
          : PublicUser.fromJson(json['author'] as Map<String, dynamic>),
      iLiked: json['iLiked'] as bool? ?? false,
    );
  }
}
