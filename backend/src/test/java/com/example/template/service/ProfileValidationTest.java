package com.example.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProfileValidationTest {

    @Test
    void acceptsACompleteForm() {
        assertNull(ProfileService.validate(valid()));
    }

    @Test
    void reportsEveryMissingRequiredFieldInOneMessage() {
        ProfileCommand incomplete = new ProfileCommand("", "", "", null, "", "", null, "", "", null, null, null);
        assertEquals("Missing required field(s): firstName, lastName, address, state, zip, phone, "
                + "communicationPreference", ProfileService.validate(incomplete));
    }

    @Test
    void rejectsTheFirstBrokenRule() {
        assertEquals("communicationPreference must be one of: email, text, phone",
                ProfileService.validate(withPreference(valid(), "carrier-pigeon")));
        assertEquals("Phone number is invalid", ProfileService.validate(withPhone(valid(), "nope")));
        assertEquals("Zip code is invalid", ProfileService.validate(withZip(valid(), "123")));
        assertEquals("State is invalid", ProfileService.validate(withState(valid(), "XX")));
        assertEquals("Country is invalid", ProfileService.validate(withCountry(valid(), "ZZ")));
        assertEquals("Additional email address is invalid", ProfileService.validate(withAltEmail(valid(), "nope")));
        assertEquals("LinkedIn URL is invalid", ProfileService.validate(withLinkedin(valid(), "nope")));
        assertEquals("GitHub URL is invalid", ProfileService.validate(withGithub(valid(), "nope")));
    }

    @Test
    void acceptsBlankOptionalFields() {
        assertNull(ProfileService.validate(new ProfileCommand("Test", "User", "1 Test St", "", "CA", "94043", "",
                "555-123-4567", "email", "", "", "")));
    }

    private static ProfileCommand valid() {
        return new ProfileCommand("Test", "User", "1 Test St", null, "CA", "94043", null, "555-123-4567",
                "email", null, null, null);
    }

    private static ProfileCommand withPreference(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), base.country(), base.phone(), value, base.linkedin(), base.github(), base.altEmail());
    }

    private static ProfileCommand withPhone(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), base.country(), value, base.communicationPreference(), base.linkedin(), base.github(),
                base.altEmail());
    }

    private static ProfileCommand withZip(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                value, base.country(), base.phone(), base.communicationPreference(), base.linkedin(), base.github(),
                base.altEmail());
    }

    private static ProfileCommand withState(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), value,
                base.zip(), base.country(), base.phone(), base.communicationPreference(), base.linkedin(),
                base.github(), base.altEmail());
    }

    private static ProfileCommand withCountry(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), value, base.phone(), base.communicationPreference(), base.linkedin(), base.github(),
                base.altEmail());
    }

    private static ProfileCommand withAltEmail(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), base.country(), base.phone(), base.communicationPreference(), base.linkedin(),
                base.github(), value);
    }

    private static ProfileCommand withLinkedin(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), base.country(), base.phone(), base.communicationPreference(), value, base.github(),
                base.altEmail());
    }

    private static ProfileCommand withGithub(ProfileCommand base, String value) {
        return new ProfileCommand(base.firstName(), base.lastName(), base.address(), base.address2(), base.state(),
                base.zip(), base.country(), base.phone(), base.communicationPreference(), base.linkedin(), value,
                base.altEmail());
    }
}
