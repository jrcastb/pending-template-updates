package com.example.pendingupdates.adapter.inmemory;

import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.port.EngagementDirectory;
import java.util.List;
import java.util.Map;

/** Reference adapter with a fixed set of engagement IDs per firm. */
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
