package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class VerifyLoginBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String token = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String code = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String deviceId = "";
}
