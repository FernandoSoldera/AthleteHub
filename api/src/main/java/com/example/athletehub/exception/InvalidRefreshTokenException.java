package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/**
 * Thrown when a refresh token is unknown, expired, or has already been
 * revoked. Mapped to HTTP 401 by {@link GlobalExceptionHandler}.
 *
 * <p>A revoked-token presentation is special: it's interpreted as token reuse
 * (likely a compromise), and {@code RefreshTokenService.rotate} revokes every
 * other active refresh token for that user before throwing this.
 */
@Getter
public class InvalidRefreshTokenException extends RuntimeException {

    private final MessageCode code;

    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
        this.code = MessageCode.INVALID_REFRESH_TOKEN;
    }
}
