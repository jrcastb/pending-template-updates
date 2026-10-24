package com.example.pendingupdates.domain;

import java.util.Objects;

/** Unique within a firm only; the same value in two firms is two different engagements. */
public record EngagementId(String value) {

    public EngagementId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("engagement id must not be blank");
        }
    }
}
