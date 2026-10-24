package com.example.pendingupdates.port;

import com.example.pendingupdates.domain.BindingSnapshot;
import com.example.pendingupdates.domain.BindingUpdate;
import com.example.pendingupdates.domain.EngagementBinding;
import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.domain.TemplateId;
import java.util.List;
import java.util.Optional;

/**
 * Store of engagement bindings. Contract for every adapter:
 *
 * <ul>
 *   <li>Rows are keyed by (firm, engagement). Every call requires a {@link FirmScope}; a call never
 *       reads or writes another firm's rows.
 *   <li>{@link #upsertIfNewer} is an atomic compare-and-write following {@link
 *       EngagementBinding#applyIfNewer}: duplicates and late snapshots never move a row backwards.
 *   <li>Results are bounded by the size of one firm (hundreds of engagements).
 * </ul>
 */
public interface BindingRepository {

    Optional<EngagementBinding> find(FirmScope scope, EngagementId engagementId);

    /**
     * Stores the snapshot if it is newer than the stored row, or creates the row.
     *
     * @throws FirmMismatch if the snapshot's firm is not the scope's firm; nothing is stored
     */
    BindingUpdate upsertIfNewer(FirmScope scope, BindingSnapshot snapshot);

    List<EngagementBinding> findByTemplate(FirmScope scope, TemplateId templateId);

    /** A write tried to cross firms. A security signal, not a retryable error. */
    final class FirmMismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FirmMismatch() {
            super("snapshot firm does not match the caller's firm scope");
        }
    }
}
