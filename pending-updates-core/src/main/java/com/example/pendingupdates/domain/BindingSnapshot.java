package com.example.pendingupdates.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Event emitted by the engagement system while it has the engagement loaded. A full snapshot, not
 * a delta: the highest revision wins, so duplicates and out-of-order delivery are harmless.
 *
 * <p>A {@code lastDeclined} at or below {@code applied} is normalized to "no decline": the producer
 * may not have cleared an old decline.
 */
public record BindingSnapshot(
        FirmId firmId,
        EngagementId engagementId,
        TemplateId templateId,
        Version applied,
        Optional<Version> lastDeclined,
        long revision,
        Cause cause) {

    public enum Cause { CREATED, OPENED, APPLIED, DECLINED }

    public BindingSnapshot {
        Objects.requireNonNull(firmId, "firmId");
        Objects.requireNonNull(engagementId, "engagementId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(applied, "applied");
        Objects.requireNonNull(lastDeclined, "lastDeclined");
        Objects.requireNonNull(cause, "cause");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative: " + revision);
        }
        lastDeclined = lastDeclined.filter(declined -> declined.isNewerThan(applied));
    }
}
