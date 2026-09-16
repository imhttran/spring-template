package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class ChangePasswordBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String currentPassword = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String newPassword = "";
}
