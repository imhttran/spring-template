package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** An email-only body: signup, resend-verification, forgot-password. */
public class EmailBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String email = "";
}
