package com.example.pendingupdates.port;

import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmScope;
import java.util.List;

/**
 * Lists a firm's registered engagement IDs without loading any engagement. In production it is the
 * engagement registry in the firm's schema, filled by snapshots and by the backfill's one-time
 * listing (assumption A4). Archived engagements are not listed. Only the scope's firm is listed;
 * the result is bounded by one firm's size.
 */
public interface EngagementDirectory {

    List<EngagementId> list(FirmScope scope);
}
