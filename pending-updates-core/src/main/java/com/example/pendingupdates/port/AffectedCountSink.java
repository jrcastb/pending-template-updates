package com.example.pendingupdates.port;

import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.TemplateId;
import com.example.pendingupdates.domain.Version;
import java.util.Objects;

/** Receives one report per firm per publish. The report doubles as the silent-firm heartbeat. */
@FunctionalInterface
public interface AffectedCountSink {

    void report(AffectedCountReport report);

    /** Counts only: no engagement IDs leave the firm. */
    record AffectedCountReport(FirmId firmId, TemplateId templateId, Version version,
            String publishId, int affected, int checking) {

        public AffectedCountReport {
            Objects.requireNonNull(firmId, "firmId");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(publishId, "publishId");
            if (affected < 0 || checking < 0) {
                throw new IllegalArgumentException("counts must not be negative");
            }
        }
    }
}
