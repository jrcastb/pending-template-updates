package com.example.pendingupdates.domain;

import java.util.Objects;

/** What the engagement list shows for one engagement. Nothing is up to date without evidence. */
public sealed interface PendingStatus {

    /** One cumulative offer {@code from -> to}, however many versions accumulated in between. */
    record UpdateAvailable(Version from, Version to, boolean includesDeclinedVersions)
            implements PendingStatus {

        public UpdateAvailable {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (!to.isNewerThan(from)) {
                throw new IllegalArgumentException("offer must move forward: " + from + " -> " + to);
            }
        }
    }

    record UpToDate() implements PendingStatus {}

    record Checking(Reason reason) implements PendingStatus {

        public Checking {
            Objects.requireNonNull(reason, "reason");
        }

        public enum Reason {
            /** No binding captured yet (for example, before backfill reaches the engagement). */
            NO_BINDING,
            /** The latest-version table has no entry for the template. */
            LATEST_UNKNOWN,
            /** The binding has seen a version newer than the latest-version table: it is lagging. */
            LATEST_BEHIND
        }
    }
}
