package com.example.template.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.api.dto.CreateUserBody;
import com.example.template.api.dto.PatchRoleBody;
import com.example.template.repository.UserRepository;
import com.example.template.service.AuthUser;
import com.example.template.service.Roles;
import com.example.template.service.UserAdminService;
import com.example.template.service.error.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Staff/admin user management. The role gate runs before the path id is parsed,
 * because that is the order the original checked them in.
 */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final UserAdminService admin;

    public UsersController(UserAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    public ResponseEntity<Object> listUsers(AuthUser user) {
        Api.requireRole(user, "staff");
        return Api.respond(HttpStatus.OK, Api.body(
                "users", admin.listUsers(Roles.hasRole(user.role(), "admin"))));
    }

    @PostMapping
    public ResponseEntity<Object> createUser(AuthUser user, @RequestBody(required = false) byte[] body) {
        Api.requireRole(user, "admin");
        CreateUserBody request = Api.decode(body, CreateUserBody.class);
        UserRepository.UserWithRole created = admin.createUser(request.email, request.password);
        return Api.respond(HttpStatus.CREATED, Api.body(
                "success", true,
                "message", "User created successfully!",
                "user", Api.body(
                        "id", created.id(),
                        "email", created.email(),
                        "role", created.role(),
                        "emailVerified", created.emailVerified())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteUser(AuthUser user, @PathVariable String id) {
        Api.requireRole(user, "admin");
        int userId = Api.parseId(id);
        try {
            admin.deleteUser(user.id(), userId);
        } catch (ValidationException rejected) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.getMessage()));
        }
        return Api.respond(HttpStatus.OK, Api.body("success", true, "message", "User deleted"));
    }

    @PatchMapping("/{id}/verification")
    public ResponseEntity<Object> patchVerification(AuthUser user, @PathVariable String id,
            @RequestBody(required = false) byte[] body) {
        Api.requireRole(user, "admin");
        int userId = Api.parseId(id);
        // Decoded loosely: a present-but-non-boolean value (string, number)
        // reads as not-a-boolean.
        JsonNode emailVerified = Api.decodeNode(body).path("emailVerified");
        if (!emailVerified.isBoolean()) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg("emailVerified must be a boolean"));
        }
        UserRepository.VerificationRow updated = admin.setVerification(userId, emailVerified.booleanValue());
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", updated.emailVerified() ? "User marked as verified" : "User marked as unverified",
                "user", Api.body(
                        "id", updated.id(),
                        "email", updated.email(),
                        "emailVerified", updated.emailVerified())));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Object> patchRole(AuthUser user, @PathVariable String id,
            @RequestBody(required = false) byte[] body) {
        Api.requireRole(user, "admin");
        int userId = Api.parseId(id);
        PatchRoleBody request = Api.decode(body, PatchRoleBody.class);
        UserRepository.RoleRow updated;
        try {
            updated = admin.setRole(user.id(), userId, request.role);
        } catch (ValidationException rejected) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.getMessage()));
        }
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "User role updated",
                "user", Api.body(
                        "id", updated.id(),
                        "email", updated.email(),
                        "role", updated.role())));
    }

    @PostMapping("/{id}/resend-verification")
    public ResponseEntity<Object> resendVerification(AuthUser user, @PathVariable String id) {
        Api.requireRole(user, "staff");
        int userId = Api.parseId(id);
        try {
            admin.resendVerification(userId);
        } catch (ValidationException rejected) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.getMessage()));
        }
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "Verification email sent"));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Object> resetPassword(AuthUser user, @PathVariable String id) {
        Api.requireRole(user, "admin");
        admin.resetPassword(Api.parseId(id));
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "Password reset email sent"));
    }
}
