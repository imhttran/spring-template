package com.example.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.example.template.config.AppProperties;

class ValidatorsTest {

    @Test
    void passwords() {
        assertNull(Validators.validatePassword("Valid123!"));
        assertEquals("Password must be at least 8 characters long", Validators.validatePassword("Ab1!"));
        assertEquals("Password must contain at least one uppercase letter",
                Validators.validatePassword("lowercase1!"));
        assertEquals("Password must contain at least one number", Validators.validatePassword("NoNumbers!"));
        assertEquals("Password must contain at least one special character",
                Validators.validatePassword("NoSpecial1"));
    }

    @Test
    void emails() {
        assertTrue(Validators.isEmail("a@b.c"));
        assertFalse(Validators.isEmail("no-at-sign"));
        assertFalse(Validators.isEmail("no@domain"));
        assertFalse(Validators.isEmail("spaces in@email.com"));
    }

    @Test
    void usStatesAndCountries() {
        assertTrue(Validators.isUsState("CA"));
        assertFalse(Validators.isUsState("XX"));
        assertTrue(Validators.isCountry("US"));
        assertFalse(Validators.isCountry("ZZ"));
    }

    @Test
    void phoneNumbers() {
        assertTrue(Validators.isPhone("555-123-4567"));
        assertTrue(Validators.isPhone("(555) 123-4567"));
        assertTrue(Validators.isPhone("+1 555 123 4567"));
        assertFalse(Validators.isPhone("12345"));
    }

    @Test
    void zipCodes() {
        assertTrue(Validators.isZip("94043"));
        assertFalse(Validators.isZip("9404"));
        assertFalse(Validators.isZip("94043-1234"));
    }

    @Test
    void urls() {
        assertTrue(Validators.isUrl("https://example.com"));
        assertTrue(Validators.isUrl("http://example.com/profile"));
        assertFalse(Validators.isUrl("ftp://example.com"));
        assertFalse(Validators.isUrl("not a url"));
    }

    @Test
    void developmentLoginCodesAreFixedAndOthersAreFourDigits() {
        AppProperties development = new AppProperties();
        development.setEnv("development");
        assertEquals("1234", new Tokens(development).randomCode());

        AppProperties test = new AppProperties();
        test.setEnv("test");
        Pattern fourDigits = Pattern.compile("\\d{4}");
        Tokens tokens = new Tokens(test);
        for (int i = 0; i < 50; i++) {
            assertTrue(fourDigits.matcher(tokens.randomCode()).matches());
        }
    }
}
