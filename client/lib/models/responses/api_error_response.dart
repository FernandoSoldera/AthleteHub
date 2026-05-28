/// Shape of the backend's 4xx/5xx envelopes (`ApiResponse` for domain errors,
/// the global handler's field-error map for validation). Used by the api
/// services to surface a stable {@code code} the UI can map to a localized
/// string via `AppLocalizations.translateErrorCode`.
class ApiErrorResponse {
  ApiErrorResponse({
    required this.statusCode,
    this.code,
    this.message,
    this.fieldErrors,
  });

  final int statusCode;
  final String? code;
  final String? message;
  final Map<String, String>? fieldErrors;

  factory ApiErrorResponse.fromJson(int statusCode, Map<String, dynamic> json) {
    Map<String, String>? fieldErrors;
    final errors = json['errors'];
    if (errors is Map) {
      fieldErrors = errors.map((k, v) => MapEntry(k.toString(), v.toString()));
    }
    return ApiErrorResponse(
      statusCode: statusCode,
      code: json['code']?.toString(),
      message: json['message']?.toString(),
      fieldErrors: fieldErrors,
    );
  }
}

/// Thrown by api services on non-2xx responses. UI catches this and shows the
/// localized message via `translateErrorCode`.
class ApiException implements Exception {
  ApiException({
    required this.statusCode,
    this.code,
    this.message,
    this.fieldErrors,
  });

  final int statusCode;
  final String? code;
  final String? message;
  final Map<String, String>? fieldErrors;

  bool get isUnauthorized => statusCode == 401;
  bool get isConflict => statusCode == 409;
  bool get isValidationFailed => statusCode == 400;

  @override
  String toString() =>
      'ApiException(status: $statusCode, code: $code, message: $message)';
}
