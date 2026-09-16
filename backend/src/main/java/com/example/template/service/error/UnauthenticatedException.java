package com.example.template.service.error;

/** Bad credentials. Mapped to a 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
