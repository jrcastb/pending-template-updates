package com.example.pendingupdates.application;

import com.example.pendingupdates.domain.EngagementBinding;
import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.domain.PendingRule;
import com.example.pendingupdates.domain.PendingStatus;
import com.example.pendingupdates.domain.TemplatePublished;
import com.example.pendingupdates.domain.Version;
import com.example.pendingupdates.port.AffectedCountSink;
import com.example.pendingupdates.port.AffectedCountSink.AffectedCountReport;
import com.example.pendingupdates.port.BindingRepository;
import com.example.pendingupdates.port.EngagementDirectory;
import java.util.Objects;
import java.util.Optional;

/**
 * Obs 1 and 2: on every publish, one firm reports how many of its engagements the new version
 * affects, and how many are still unknown. It always reports, even 0/0, so a missing report means
 * a silent firm. Idempotent: a redelivered publish reports the same counts.
 */
public final class AffectedCountOnPublish {

    private final EngagementDirectory directory;
    private final BindingRepository bindings;
    private final AffectedCountSink sink;

    public AffectedCountOnPublish(EngagementDirectory directory, BindingRepository bindings,
            AffectedCountSink sink) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public void handle(FirmScope scope, TemplatePublished event) {
        Optional<Version> latest = Optional.of(event.version());
        int affected = 0;
        for (EngagementBinding binding : bindings.findByTemplate(scope, event.templateId())) {
            // "Affected" is the pending rule with latest = the new version.
            switch (PendingRule.evaluate(Optional.of(binding), latest)) {
                case PendingStatus.UpdateAvailable offer -> affected++;
                case PendingStatus.UpToDate upToDate -> { }
                case PendingStatus.Checking checking -> { } // binding ahead of this publish
            }
        }
        // Without a binding the template is unknown, so it counts as checking on every publish.
        int checking = 0;
        for (EngagementId id : directory.list(scope)) {
            if (bindings.find(scope, id).isEmpty()) {
                checking++;
            }
        }
        sink.report(new AffectedCountReport(scope.firmId(), event.templateId(), event.version(),
                event.publishId(), affected, checking));
    }
}
