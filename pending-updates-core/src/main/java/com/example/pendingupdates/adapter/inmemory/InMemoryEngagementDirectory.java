package com.example.pendingupdates.adapter.inmemory;

import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.port.EngagementDirectory;
import java.util.List;
import java.util.Map;

/** Stands in for the registry; registration on snapshot is design-only. */
public final class InMemoryEngagementDirectory implements EngagementDirectory {

    private final Map<FirmId, List<EngagementId>> idsByFirm;

    public InMemoryEngagementDirectory(Map<FirmId, List<EngagementId>> idsByFirm) {
        this.idsByFirm = Map.copyOf(idsByFirm);
    }

    @Override
    public List<EngagementId> list(FirmScope scope) {
        return idsByFirm.getOrDefault(scope.firmId(), List.of());
    }
}
