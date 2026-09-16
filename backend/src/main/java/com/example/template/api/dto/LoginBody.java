package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class LoginBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String email = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String password = "";

    /** Empty when the client doesn't send one — then 2FA always applies. */
    @JsonSetter(nulls = Nulls.SKIP)
    public String deviceId = "";
}
