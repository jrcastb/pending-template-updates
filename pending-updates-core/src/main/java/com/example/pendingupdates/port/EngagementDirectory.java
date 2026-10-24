package com.example.pendingupdates.port;

import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmScope;
import java.util.List;

/**
 * Lists a firm's engagement IDs without loading any engagement (assumption A4). Only the scope's
 * firm is listed; the result is bounded by one firm's size.
 */
public interface EngagementDirectory {

    List<EngagementId> list(FirmScope scope);
}
