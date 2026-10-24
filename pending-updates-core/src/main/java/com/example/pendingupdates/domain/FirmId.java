package com.example.pendingupdates.domain;

import java.util.Objects;

public record FirmId(String value) {

    public FirmId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("firm id must not be blank");
        }
    }
}
