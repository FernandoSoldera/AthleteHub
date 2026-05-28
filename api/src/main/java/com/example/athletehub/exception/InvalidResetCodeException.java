package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/**
 * Thrown when a password-reset code is unknown, expired, or already consumed.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler} — the code is treated
 * as user-supplied data that didn't validate, not as a failed authentication.
 */
@Getter
public class InvalidResetCodeException extends RuntimeException {

    private final MessageCode code;

    public InvalidResetCodeException() {
        super("Invalid or expired reset code");
        this.code = MessageCode.INVALID_RESET_CODE;
    }
}
