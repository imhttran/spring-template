package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Admin-created user: the admin picks the first password. */
public class CreateUserBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String email = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String password = "";
}
