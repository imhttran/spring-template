package com.example.template.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.template.repository.Profile;
import com.example.template.repository.ProfileRepository;
import com.example.template.service.error.ConflictException;
import com.example.template.service.error.ServerErrorException;
import com.example.template.service.error.ValidationException;

/**
 * The one-time registration form: submitted once per user, and a missing row
 * (not a boolean flag) is what gates a user into the completion form.
 */
@Service
public class ProfileService {

    private static final List<String> COMMUNICATION_PREFERENCES = List.of("email", "text", "phone");

    private static final String DEFAULT_COUNTRY = "US";

    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** A missing profile is an empty result, not an error — the absence is the gate. */
    public Optional<Profile> getProfile(int userId) {
        try {
            return profiles.findByUserId(userId);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Get Profile Error", failed, false);
        }
    }

    public Profile saveProfile(int userId, ProfileCommand command) {
        String validationError = validate(command);
        if (validationError != null) {
            throw new ValidationException(validationError);
        }
        // A blank country falls back to US.
        String country = command.country() == null ? "" : command.country().trim();
        if (country.isEmpty()) {
            country = DEFAULT_COUNTRY;
        }
        Profile row = new Profile(
                0,
                userId,
                command.firstName().trim(),
                command.lastName().trim(),
                command.address().trim(),
                optionalTrimmed(command.address2()),
                command.state().trim(),
                command.zip().trim(),
                country,
                command.phone().trim(),
                command.communicationPreference(),
                optionalTrimmed(command.linkedin()),
                optionalTrimmed(command.github()),
                optionalTrimmed(command.altEmail()));
        try {
            return profiles.insert(row);
        } catch (DuplicateKeyException alreadyExists) {
            throw new ConflictException("Profile already exists");
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Save Profile Error", failed, false);
        }
    }

    /**
     * @return a message describing the first unmet rule, or null when the form
     *         is valid.
     */
    static String validate(ProfileCommand command) {
        List<String> missing = new ArrayList<>();
        collectMissing(missing, "firstName", command.firstName());
        collectMissing(missing, "lastName", command.lastName());
        collectMissing(missing, "address", command.address());
        collectMissing(missing, "state", command.state());
        collectMissing(missing, "zip", command.zip());
        collectMissing(missing, "phone", command.phone());
        collectMissing(missing, "communicationPreference", command.communicationPreference());
        if (!missing.isEmpty()) {
            return "Missing required field(s): " + String.join(", ", missing);
        }
        if (!COMMUNICATION_PREFERENCES.contains(command.communicationPreference())) {
            return "communicationPreference must be one of: "
                    + String.join(", ", COMMUNICATION_PREFERENCES);
        }
        if (!Validators.isPhone(command.phone())) {
            return "Phone number is invalid";
        }
        if (!Validators.isZip(command.zip())) {
            return "Zip code is invalid";
        }
        if (!Validators.isUsState(command.state())) {
            return "State is invalid";
        }
        // The dropdown only offers what's in the country list, but a direct API
        // call could still send something else.
        if (!isBlank(command.country()) && !Validators.isCountry(command.country())) {
            return "Country is invalid";
        }
        if (!isBlank(command.altEmail()) && !Validators.isEmail(command.altEmail())) {
            return "Additional email address is invalid";
        }
        if (!isBlank(command.linkedin()) && !Validators.isUrl(command.linkedin())) {
            return "LinkedIn URL is invalid";
        }
        if (!isBlank(command.github()) && !Validators.isUrl(command.github())) {
            return "GitHub URL is invalid";
        }
        return null;
    }

    private static void collectMissing(List<String> missing, String field, String value) {
        if (isBlank(value) || value.isBlank()) {
            missing.add(field);
        }
    }

    /** {@code body.x?.trim() || null} — blank optionals are stored as NULL. */
    private static String optionalTrimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
