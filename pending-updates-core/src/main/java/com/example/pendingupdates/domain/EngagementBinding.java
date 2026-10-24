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

    public static EngagementBinding from(BindingSnapshot snapshot) {
        return new EngagementBinding(snapshot.firmId(), snapshot.engagementId(),
                snapshot.templateId(), snapshot.applied(), snapshot.lastDeclined(),
                snapshot.revision());
    }

    /**
     * Idempotent, order-independent update: only a higher revision changes the binding.
     *
     * @throws TemplateMismatch if the snapshot names another template (assumption A3)
     * @throws ConflictingRevision if the same revision carries different content (producer bug)
     */
    public BindingUpdate applyIfNewer(BindingSnapshot snapshot) {
        if (!snapshot.firmId().equals(firmId) || !snapshot.engagementId().equals(engagementId)) {
            throw new IllegalArgumentException("snapshot belongs to another engagement");
        }
        if (!snapshot.templateId().equals(templateId)) {
            throw new TemplateMismatch(templateId, snapshot.templateId());
        }
        EngagementBinding candidate = from(snapshot);
        if (snapshot.revision() > revision) {
            return new BindingUpdate.Changed(candidate);
        }
        if (snapshot.revision() < revision) {
            return new BindingUpdate.Ignored(BindingUpdate.Ignored.Reason.STALE);
        }
        if (!candidate.equals(this)) {
            throw new ConflictingRevision(revision);
        }
        return new BindingUpdate.Ignored(BindingUpdate.Ignored.Reason.DUPLICATE);
    }

    /** Same revision, different content. In production: DLQ plus alarm, not a consumer crash. */
    public static final class ConflictingRevision extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConflictingRevision(long revision) {
            super("conflicting content for revision " + revision);
        }
    }

    /** An engagement's template never changes (A3): a producer bug. In production: DLQ plus alarm. */
    public static final class TemplateMismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TemplateMismatch(TemplateId expected, TemplateId actual) {
            super("template mismatch: expected " + expected.value() + ", got " + actual.value());
        }
    }
}
