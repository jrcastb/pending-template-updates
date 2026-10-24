package com.example.pendingupdates.domain;

import java.util.Objects;

public record TemplateId(String value) {

    public TemplateId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("template id must not be blank");
        }
    }
}
