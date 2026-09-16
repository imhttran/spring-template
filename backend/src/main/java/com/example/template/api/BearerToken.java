package com.example.template.api;

import jakarta.servlet.http.HttpServletRequest;

/** Reads the JWT out of {@code Authorization: Bearer <token>}. */
final class BearerToken {

    private BearerToken() {
    }

    /** The second space-separated part, or empty when there isn't one. */
    static String from(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return "";
        }
        String[] parts = header.split(" ");
        return parts.length > 1 ? parts[1] : "";
    }
}
