package com.example.pendingupdates.adapter.inmemory;

import com.example.pendingupdates.domain.BindingSnapshot;
import com.example.pendingupdates.domain.BindingUpdate;
import com.example.pendingupdates.domain.EngagementBinding;
import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.domain.TemplateId;
import com.example.pendingupdates.port.BindingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reference adapter. Rows are partitioned per firm, mirroring one store per firm. */
public final class InMemoryBindingRepository implements BindingRepository {

    private final Map<FirmId, Map<EngagementId, EngagementBinding>> rowsByFirm =
            new ConcurrentHashMap<>();

    @Override
    public Optional<EngagementBinding> find(FirmScope scope, EngagementId engagementId) {
        return Optional.ofNullable(rowsOf(scope).get(engagementId));
    }

    @Override
    public BindingUpdate upsertIfNewer(FirmScope scope, BindingSnapshot snapshot) {
        if (!snapshot.firmId().equals(scope.firmId())) {
            throw new FirmMismatch();
        }
        BindingUpdate[] outcome = new BindingUpdate[1];
        // compute() is atomic per key; an exception leaves the row unchanged.
        Map<EngagementId, EngagementBinding> rows =
                rowsByFirm.computeIfAbsent(scope.firmId(), firm -> new ConcurrentHashMap<>());
        rows.compute(snapshot.engagementId(), (id, current) -> {
            outcome[0] = current == null
                    ? new BindingUpdate.Changed(EngagementBinding.from(snapshot))
                    : current.applyIfNewer(snapshot);
            return switch (outcome[0]) {
                case BindingUpdate.Changed changed -> changed.binding();
                case BindingUpdate.Ignored ignored -> current;
            };
        });
        return outcome[0];
    }

    @Override
    public List<EngagementBinding> findByTemplate(FirmScope scope, TemplateId templateId) {
        return rowsOf(scope).values().stream()
                .filter(binding -> binding.templateId().equals(templateId))
                .toList();
    }

    private Map<EngagementId, EngagementBinding> rowsOf(FirmScope scope) {
        return rowsByFirm.getOrDefault(scope.firmId(), Map.of());
    }
}
