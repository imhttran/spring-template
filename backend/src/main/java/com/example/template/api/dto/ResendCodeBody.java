package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** The pending-login token, for resending a 2FA code. */
public class ResendCodeBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String token = "";
}
