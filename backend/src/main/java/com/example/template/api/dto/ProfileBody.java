package com.example.template.api.dto;

import com.example.template.service.ProfileCommand;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * The registration form. Required fields default to empty strings; the optional
 * ones stay null when absent.
 */
public class ProfileBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String firstName = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String lastName = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String address = "";

    public String address2;

    @JsonSetter(nulls = Nulls.SKIP)
    public String state = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String zip = "";

    /** Blank falls back to US. */
    public String country;

    @JsonSetter(nulls = Nulls.SKIP)
    public String phone = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String communicationPreference = "";

    public String linkedin;

    public String github;

    public String altEmail;

    public ProfileCommand toCommand() {
        return new ProfileCommand(
            firstName,
            lastName,
            address,
            address2,
            state,
            zip,
            country,
            phone,
            communicationPreference,
            linkedin,
            github,
            altEmail
        );
    }
}
