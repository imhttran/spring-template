package com.example.template.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.api.dto.ProfileBody;
import com.example.template.repository.Profile;
import com.example.template.service.AuthUser;
import com.example.template.service.ProfileService;
import com.example.template.service.error.ConflictException;
import com.example.template.service.error.ValidationException;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<Object> getProfile(AuthUser user) {
        // A missing profile is a 200 with null, not a 404 — the absence is the
        // gate, not an error.
        return Api.respond(HttpStatus.OK, Api.body("profile", profiles.getProfile(user.id()).orElse(null)));
    }

    @PostMapping
    public ResponseEntity<Object> saveProfile(AuthUser user, @RequestBody(required = false) byte[] body) {
        ProfileBody request = Api.decode(body, ProfileBody.class);
        Profile saved;
        try {
            saved = profiles.saveProfile(user.id(), request.toCommand());
        } catch (ValidationException | ConflictException rejected) {
            // This route's 400s are the bare-message shape, not the
            // success-false one, so the shared defaults don't apply.
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.getMessage()));
        }
        return Api.respond(HttpStatus.CREATED, Api.body(
                "success", true,
                "message", "Profile saved!",
                "profile", saved));
    }
}
