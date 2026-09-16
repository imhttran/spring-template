package com.example.template.service.error;

/** Allowed to sign in, not allowed to proceed. Mapped to a 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
