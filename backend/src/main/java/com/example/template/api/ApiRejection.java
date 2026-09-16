package com.example.template.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A response the API layer has already decided on — the auth gates, the role
 * gates and the path-id parser. Those responses are always the bare
 * {@code {"message": …}} shape, whatever their status, so they can't share the
 * per-exception defaults the service exceptions use.
 */
public class ApiRejection extends RuntimeException {

    private final transient ResponseEntity<Object> response;

    public ApiRejection(HttpStatus status, Map<String, Object> body) {
        super(String.valueOf(body.get("message")));
        this.response = ResponseEntity.status(status).body(body);
    }

    public ResponseEntity<Object> response() {
        return response;
    }
}
