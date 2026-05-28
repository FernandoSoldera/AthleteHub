package com.example.athletehub.exception;

import com.example.athletehub.enums.MessageCode;
import lombok.Getter;

/** Thrown when a requested entity does not exist. Mapped to HTTP 404. */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final MessageCode code;

    public ResourceNotFoundException(String message) {
        super(message);
        this.code = null;
    }

    public ResourceNotFoundException(MessageCode code) {
        super(code != null ? code.name() : null);
        this.code = code;
    }
}
