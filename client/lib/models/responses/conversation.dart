import '../public_user.dart';

/// One inbox row — the hydrated thread view, with the other side of the
/// conversation pre-loaded so the inbox list doesn't need a second
/// round-trip per row. Mirrors backend `ConversationDto`. `unreadCount`
/// counts the peer's messages that arrived after the viewer's read
/// pointer — never the viewer's own sends.
class Conversation {
  Conversation({
    required this.id,
    required this.unreadCount,
    this.coachAthleteId,
    this.lastMessageAt,
    this.lastMessagePreview,
    this.peer,
  });

  final int id;
  final int? coachAthleteId;
  final DateTime? lastMessageAt;
  final String? lastMessagePreview;
  final int unreadCount;
  final PublicUser? peer;

  factory Conversation.fromJson(Map<String, dynamic> json) {
    return Conversation(
      id: (json['id'] as num).toInt(),
      coachAthleteId: (json['coachAthleteId'] as num?)?.toInt(),
      lastMessageAt: json['lastMessageAt'] == null
          ? null
          : DateTime.tryParse(json['lastMessageAt'] as String),
      lastMessagePreview: json['lastMessagePreview'] as String?,
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
      peer: json['peer'] == null
          ? null
          : PublicUser.fromJson(json['peer'] as Map<String, dynamic>),
    );
  }
}
