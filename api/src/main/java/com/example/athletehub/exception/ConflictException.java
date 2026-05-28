package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/** Thrown when a request would violate a uniqueness/state precondition. Mapped to HTTP 409. */
@Getter
public class ConflictException extends RuntimeException {

    private final MessageCode code;

    public ConflictException(String message) {
        super(message);
        this.code = null;
    }

    public ConflictException(MessageCode code) {
        super(code != null ? code.name() : null);
        this.code = code;
    }
}
