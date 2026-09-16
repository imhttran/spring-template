package com.example.template.service.error;

/** No such record. Mapped to a 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
