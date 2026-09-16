package com.example.template.service.error;

/** Rate limit hit. Mapped to a 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
