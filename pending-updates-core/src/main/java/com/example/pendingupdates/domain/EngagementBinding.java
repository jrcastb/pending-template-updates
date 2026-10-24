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
     * Pure decision for one snapshot against the stored binding, if any. Idempotent and
     * order-independent: only a higher revision changes the binding. Adapters persist the result
     * atomically and hold no rules of their own.
     *
     * @throws TemplateMismatch if the snapshot names another template (assumption A3)
     * @throws ConflictingRevision if the same revision carries different content (producer bug)
     */
    public static BindingUpdate applyIfNewer(Optional<EngagementBinding> current,
            BindingSnapshot snapshot) {
        EngagementBinding candidate = from(snapshot);
        if (current.isEmpty()) {
            return new BindingUpdate.Created(candidate);
        }
        EngagementBinding stored = current.get();
        if (!snapshot.firmId().equals(stored.firmId)
                || !snapshot.engagementId().equals(stored.engagementId)) {
            throw new IllegalArgumentException("snapshot belongs to another engagement");
        }
        if (!snapshot.templateId().equals(stored.templateId)) {
            throw new TemplateMismatch(stored.templateId, snapshot.templateId());
        }
        if (snapshot.revision() > stored.revision) {
            return new BindingUpdate.Changed(candidate);
        }
        if (snapshot.revision() < stored.revision) {
            return new BindingUpdate.Ignored(BindingUpdate.Ignored.Reason.STALE);
        }
        if (!candidate.equals(stored)) {
            throw new ConflictingRevision(stored.revision);
        }
        return new BindingUpdate.Ignored(BindingUpdate.Ignored.Reason.DUPLICATE);
    }

    /** A producer bug, never retried. In production: DLQ plus alarm, not a consumer crash. */
    public abstract static sealed class RejectedSnapshot extends RuntimeException
            permits ConflictingRevision, TemplateMismatch {
        private static final long serialVersionUID = 1L;

        RejectedSnapshot(String message) {
            super(message);
        }
    }

    /** Same revision, different content. */
    public static final class ConflictingRevision extends RejectedSnapshot {
        private static final long serialVersionUID = 1L;

        ConflictingRevision(long revision) {
            super("conflicting content for revision " + revision);
        }
    }

    /** An engagement's template never changes (A3). */
    public static final class TemplateMismatch extends RejectedSnapshot {
        private static final long serialVersionUID = 1L;

        TemplateMismatch(TemplateId expected, TemplateId actual) {
            super("template mismatch: expected " + expected.value() + ", got " + actual.value());
        }
    }
}
