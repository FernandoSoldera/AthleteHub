/// One message in a thread. Mirrors backend `MessageDto`. `attachmentMediaId`
/// stays here as a nullable int so the model survives AH-092's media upload
/// landing without a refactor.
class Message {
  Message({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.body,
    required this.createdAt,
    this.attachmentMediaId,
  });

  final int id;
  final int conversationId;
  final int senderId;
  final String body;
  final int? attachmentMediaId;
  final DateTime createdAt;

  factory Message.fromJson(Map<String, dynamic> json) {
    return Message(
      id: (json['id'] as num).toInt(),
      conversationId: (json['conversationId'] as num).toInt(),
      senderId: (json['senderId'] as num).toInt(),
      body: json['body'] as String,
      attachmentMediaId: (json['attachmentMediaId'] as num?)?.toInt(),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}
