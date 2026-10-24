package com.example.pendingupdates.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pendingupdates.adapter.inmemory.InMemoryBindingRepository;
import com.example.pendingupdates.adapter.inmemory.InMemoryEngagementDirectory;
import com.example.pendingupdates.domain.BindingSnapshot;
import com.example.pendingupdates.domain.BindingSnapshot.Cause;
import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.domain.TemplateId;
import com.example.pendingupdates.domain.TemplatePublished;
import com.example.pendingupdates.domain.Version;
import com.example.pendingupdates.port.AffectedCountSink.AffectedCountReport;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AffectedCountOnPublishTest {

    private static final FirmId FIRM_A = new FirmId("firm-a");
    private static final FirmId FIRM_B = new FirmId("firm-b");
    private static final TemplateId AUDIT = new TemplateId("audit");
    private static final TemplatePublished AUDIT_V3 =
            new TemplatePublished(AUDIT, new Version(3), "publish-1");

    private final InMemoryBindingRepository bindings = new InMemoryBindingRepository();
    private final List<AffectedCountReport> reports = new ArrayList<>();

    @Test
    void reportsAffectedAndCheckingCountsForAMixedFirm() {
        // given firm A: affected, up to date, declined, other template, no binding
        bind(FIRM_A, "affected", AUDIT, 2, 0);
        bind(FIRM_A, "up-to-date", AUDIT, 3, 0);
        bind(FIRM_A, "declined", AUDIT, 2, 3);
        bind(FIRM_A, "other-template", new TemplateId("review"), 1, 0);
        bind(FIRM_B, "affected", AUDIT, 1, 0);
        var handler = handler(Map.of(FIRM_A, ids("affected", "up-to-date", "declined",
                "other-template", "no-binding")));

        // when
        handler.handle(new FirmScope(FIRM_A), AUDIT_V3);

        // then firm B's binding is not counted
        assertThat(reports).containsExactly(new AffectedCountReport(FIRM_A, AUDIT,
                new Version(3), "publish-1", 1, 1));
    }

    @Test
    void emptyFirmStillReportsZeroAsAHeartbeat() {
        handler(Map.of()).handle(new FirmScope(FIRM_A), AUDIT_V3);

        assertThat(reports).containsExactly(new AffectedCountReport(FIRM_A, AUDIT,
                new Version(3), "publish-1", 0, 0));
    }

    @Test
    void redeliveredPublishReportsTheSameCounts() {
        bind(FIRM_A, "affected", AUDIT, 2, 0);
        var handler = handler(Map.of(FIRM_A, ids("affected")));

        handler.handle(new FirmScope(FIRM_A), AUDIT_V3);
        handler.handle(new FirmScope(FIRM_A), AUDIT_V3);

        assertThat(reports).hasSize(2).first().isEqualTo(reports.get(1));
    }

    @Test
    void reportCarriesNoEngagementIds() {
        assertThat(AffectedCountReport.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .noneMatch(type -> type == EngagementId.class
                        || Collection.class.isAssignableFrom(type));
    }

    private AffectedCountOnPublish handler(Map<FirmId, List<EngagementId>> idsByFirm) {
        return new AffectedCountOnPublish(new InMemoryEngagementDirectory(idsByFirm), bindings,
                reports::add);
    }

    private void bind(FirmId firm, String engagement, TemplateId template, int applied,
            int declined) {
        Optional<Version> lastDeclined =
                declined == 0 ? Optional.empty() : Optional.of(new Version(declined));
        bindings.upsertIfNewer(new FirmScope(firm), new BindingSnapshot(firm,
                new EngagementId(engagement), template, new Version(applied), lastDeclined, 1L,
                Cause.OPENED));
    }

    private static List<EngagementId> ids(String... values) {
        return Arrays.stream(values).map(EngagementId::new).toList();
    }
}
