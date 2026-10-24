package com.example.pendingupdates.domain;

import java.util.Objects;

/**
 * The firm a caller is authorized for. Required by every repository call.
 *
 * <p>Derived from the authenticated user token on API paths; on event paths, from the event's
 * {@code firmId}, accepted only from the verified producer (the engagement system's role). Never
 * from a request body. In this slice it is a plain record; that mapping is out of scope.
 */
public record FirmScope(FirmId firmId) {

    public FirmScope {
        Objects.requireNonNull(firmId, "firmId");
    }
}
