package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/**
 * Thrown by our service layer when login credentials don't match. Mapped to
 * HTTP 401 by {@link GlobalExceptionHandler}.
 *
 * <p>We use this <em>instead</em> of Spring Security's {@code BadCredentialsException}
 * so the exception is handled by our advice (consistent JSON body) and not
 * intercepted by Spring Security's {@code ExceptionTranslationFilter} (which
 * would call the {@code AuthenticationEntryPoint} and bypass our handler).
 */
@Getter
public class InvalidCredentialsException extends RuntimeException {

    private final MessageCode code;

    public InvalidCredentialsException() {
        super("Invalid credentials");
        this.code = MessageCode.INVALID_CREDENTIALS;
    }
}
