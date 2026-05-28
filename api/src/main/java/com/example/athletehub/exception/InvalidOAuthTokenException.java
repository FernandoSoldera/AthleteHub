package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/**
 * Thrown when a presented OAuth ID token fails verification (bad signature,
 * wrong issuer, wrong audience, expired, or missing required claims).
 * Mapped to HTTP 401 by {@link GlobalExceptionHandler}.
 */
@Getter
public class InvalidOAuthTokenException extends RuntimeException {

    private final MessageCode code;

    public InvalidOAuthTokenException() {
        super("Invalid OAuth ID token");
        this.code = MessageCode.INVALID_OAUTH_TOKEN;
    }

    public InvalidOAuthTokenException(String message) {
        super(message);
        this.code = MessageCode.INVALID_OAUTH_TOKEN;
    }
}
