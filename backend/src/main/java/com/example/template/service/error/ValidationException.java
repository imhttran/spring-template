package com.example.template.service.error;

/** Input the caller can fix. Mapped to a 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
