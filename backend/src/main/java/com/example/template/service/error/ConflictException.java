package com.example.template.service.error;

/**
 * The change collided with something that already exists (in practice, a
 * unique email or an existing profile). Mapped to a 400.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
