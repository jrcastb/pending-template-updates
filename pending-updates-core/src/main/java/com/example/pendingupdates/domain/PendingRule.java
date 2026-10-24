package com.example.pendingupdates.domain;

import com.example.pendingupdates.domain.PendingStatus.Checking;
import com.example.pendingupdates.domain.PendingStatus.Checking.Reason;
import com.example.pendingupdates.domain.PendingStatus.UpToDate;
import com.example.pendingupdates.domain.PendingStatus.UpdateAvailable;
import java.util.Optional;

/**
 * {@code pending = latest > max(applied, lastDeclined)}, derived on read and never stored.
 *
 * <p>The caller passes the latest version of the binding's own template.
 */
public final class PendingRule {

    private PendingRule() {}

    public static PendingStatus evaluate(Optional<EngagementBinding> binding,
            Optional<Version> latest) {
        if (binding.isEmpty()) {
            return new Checking(Reason.NO_BINDING);
        }
        if (latest.isEmpty()) {
            return new Checking(Reason.LATEST_UNKNOWN);
        }
        Version applied = binding.get().applied();
        Optional<Version> declined = binding.get().lastDeclined()
                .filter(d -> d.isNewerThan(applied));
        Version handled = declined.orElse(applied);
        Version newest = latest.get();

        if (handled.isNewerThan(newest)) {
            return new Checking(Reason.LATEST_BEHIND);
        }
        if (newest.isNewerThan(handled)) {
            return new UpdateAvailable(applied, newest, declined.isPresent());
        }
        return new UpToDate();
    }
}
