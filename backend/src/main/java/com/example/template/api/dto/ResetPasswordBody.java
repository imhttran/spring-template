package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class ResetPasswordBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String token = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String password = "";
}
