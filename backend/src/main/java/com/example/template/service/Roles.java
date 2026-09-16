package com.example.template.service;

import java.util.List;

/**
 * Ranked lowest to highest: a role satisfies a check for itself or anything
 * below it.
 */
public final class Roles {

    public static final List<String> ROLES = List.of("client", "staff", "admin");

    private Roles() {
    }

    public static boolean isRole(String role) {
        return ROLES.contains(role);
    }

    public static int indexOf(String role) {
        return ROLES.indexOf(role);
    }

    public static boolean hasRole(String userRole, String minimumRole) {
        int user = indexOf(userRole);
        int minimum = indexOf(minimumRole);
        return user >= 0 && minimum >= 0 && user >= minimum;
    }
}
