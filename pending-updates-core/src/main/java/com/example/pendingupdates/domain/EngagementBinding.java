package com.example.pendingupdates.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Read model of one engagement's template state, captured from events the engagement system emits
 * while it already has the engagement loaded. Keyed by (firm, engagement).
 */
public record EngagementBinding(
        FirmId firmId,
        EngagementId engagementId,
        TemplateId templateId,
        Version applied,
        Optional<Version> lastDeclined,
        long revision) {

    public EngagementBinding {
        Objects.requireNonNull(firmId, "firmId");
        Objects.requireNonNull(engagementId, "engagementId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(applied, "applied");
        Objects.requireNonNull(lastDeclined, "lastDeclined");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative: " + revision);
        }
    }
}
