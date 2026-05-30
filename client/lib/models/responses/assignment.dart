/// One assignment row as exposed to the client. Mirrors backend
/// `AssignmentDto`. `relationshipId` surfaces the FK to `coach_athlete` so
/// the client can map an assignment back to its coach when listing across
/// multiple relationships.
class Assignment {
  Assignment({
    required this.id,
    required this.relationshipId,
    required this.type,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
    this.refType,
    this.refId,
    this.scheduledFor,
    this.notes,
  });

  final int id;
  final int relationshipId;
  final String type;        // workout | diet | eval
  final String? refType;    // workout_template | diet_plan | eval_request
  final int? refId;
  final DateTime? scheduledFor;
  final String status;      // scheduled | today | pending | done | skipped
  final String? notes;
  final DateTime createdAt;
  final DateTime updatedAt;

  bool get isDone => status == 'done';
  bool get isSkipped => status == 'skipped';

  factory Assignment.fromJson(Map<String, dynamic> json) {
    return Assignment(
      id: (json['id'] as num).toInt(),
      relationshipId: (json['relationshipId'] as num).toInt(),
      type: json['type'] as String,
      refType: json['refType'] as String?,
      refId: (json['refId'] as num?)?.toInt(),
      scheduledFor: json['scheduledFor'] == null
          ? null
          : DateTime.parse(json['scheduledFor'] as String),
      status: json['status'] as String,
      notes: json['notes'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}
