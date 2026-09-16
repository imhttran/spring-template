package com.example.template.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.api.dto.ChangePasswordBody;
import com.example.template.api.dto.EmailBody;
import com.example.template.api.dto.LoginBody;
import com.example.template.api.dto.ResendCodeBody;
import com.example.template.api.dto.ResetPasswordBody;
import com.example.template.api.dto.SignupBody;
import com.example.template.api.dto.VerifyLoginBody;
import com.example.template.service.AuthService;
import com.example.template.service.AuthUser;

/**
 * Public auth routes plus the signed-in self-service ones. Bodies are read as
 * raw bytes so an unparsable body behaves like an empty one (see {@link Api}).
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    public ResponseEntity<Object> signup(@RequestBody(required = false) byte[] body) {
        SignupBody request = Api.decode(body, SignupBody.class);
        auth.signup(request.email, request.password);
        return Api.respond(HttpStatus.CREATED, Api.body(
                "success", true,
                "message", "User created successfully!",
                "user", Api.body("email", request.email)));
    }

    @GetMapping("/verify")
    public ResponseEntity<Object> verify(@RequestParam(name = "token", required = false) String token) {
        auth.verify(token == null ? "" : token);
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "Email verified successfully!"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Object> resendVerification(@RequestBody(required = false) byte[] body) {
        EmailBody request = Api.decode(body, EmailBody.class);
        auth.resendVerification(request.email);
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "If that email is registered and unverified, a verification link has been sent."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Object> forgotPassword(@RequestBody(required = false) byte[] body) {
        EmailBody request = Api.decode(body, EmailBody.class);
        auth.forgotPassword(request.email);
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Object> resetPassword(@RequestBody(required = false) byte[] body) {
        ResetPasswordBody request = Api.decode(body, ResetPasswordBody.class);
        AuthService.PasswordReset reset = auth.resetPassword(request.token, request.password);
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "Password reset successfully!",
                "token", reset.token(),
                "user", Api.body("email", reset.email())));
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody(required = false) byte[] body) {
        LoginBody request = Api.decode(body, LoginBody.class);
        return loginResponse(auth.login(request.email, request.password, request.deviceId));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<Object> verifyLogin(@RequestBody(required = false) byte[] body) {
        VerifyLoginBody request = Api.decode(body, VerifyLoginBody.class);
        return loginResponse(auth.verifyLogin(request.token, request.code, request.deviceId));
    }

    @PostMapping("/login/resend")
    public ResponseEntity<Object> resendLoginCode(@RequestBody(required = false) byte[] body) {
        ResendCodeBody request = Api.decode(body, ResendCodeBody.class);
        auth.resendLoginCode(request.token);
        return Api.respond(HttpStatus.OK, Api.msg("Code resent"));
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(AuthUser user) {
        return Api.respond(HttpStatus.OK, Api.body(
                "message", "Welcome to the secret area!",
                "user", Api.body(
                        "id", user.id(),
                        "email", user.email(),
                        "role", user.role(),
                        "emailVerified", user.emailVerified(),
                        "mustChangePassword", user.mustChangePassword(),
                        "hasProfile", user.hasProfile())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Object> changePassword(AuthUser user, @RequestBody(required = false) byte[] body) {
        ChangePasswordBody request = Api.decode(body, ChangePasswordBody.class);
        auth.changePassword(user.id(), user.passwordHash(), request.currentPassword, request.newPassword);
        return Api.respond(HttpStatus.OK, Api.body(
                "success", true,
                "message", "Password changed successfully!"));
    }

    /** A trusted device logs straight in; a new one gets a pending 2FA token instead. */
    private static ResponseEntity<Object> loginResponse(AuthService.LoginResult result) {
        return switch (result) {
            case AuthService.LoginResult.Authenticated authenticated -> Api.respond(HttpStatus.OK, Api.body(
                    "success", true,
                    "message", "Login successful!",
                    "token", authenticated.token(),
                    "user", Api.body("email", authenticated.email())));
            case AuthService.LoginResult.TwoFactorRequired pending -> Api.respond(HttpStatus.OK, Api.body(
                    "success", true,
                    "twoFactorRequired", true,
                    "token", pending.pendingToken(),
                    "message", "Enter the code sent to your device"));
        };
    }
}
