package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/** Thrown for invalid client input / broken preconditions. Mapped to HTTP 400. */
@Getter
public class BadRequestException extends RuntimeException {

    private final MessageCode code;

    public BadRequestException(String message) {
        super(message);
        this.code = null;
    }

    public BadRequestException(MessageCode code) {
        super(code != null ? code.name() : null);
        this.code = code;
    }
}
