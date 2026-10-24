package com.example.pendingupdates.domain;

import java.util.Objects;

/** Outcome of offering a snapshot to a binding. */
public sealed interface BindingUpdate {

    record Changed(EngagementBinding binding) implements BindingUpdate {

        public Changed {
            Objects.requireNonNull(binding, "binding");
        }
    }

    record Ignored(Reason reason) implements BindingUpdate {

        public Ignored {
            Objects.requireNonNull(reason, "reason");
        }

        public enum Reason {
            /** Same revision, same content: a redelivery. */
            DUPLICATE,
            /** Older revision than the stored one: a late event. */
            STALE
        }
    }
}
