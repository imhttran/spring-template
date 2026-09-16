package com.example.template.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Request bodies are mutable objects with real defaults: a missing field keeps
 * its default, and an explicit {@code null} is skipped rather than wiping it
 * (treating null as "the field was absent" is what the contract expects).
 * Validation is what rejects bad input, not decoding.
 */
public class SignupBody {

    @JsonSetter(nulls = Nulls.SKIP)
    public String email = "";

    @JsonSetter(nulls = Nulls.SKIP)
    public String password = "";
}
