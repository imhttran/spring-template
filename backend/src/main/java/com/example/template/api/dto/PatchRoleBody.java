package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class PatchRoleBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String role = "";
}
