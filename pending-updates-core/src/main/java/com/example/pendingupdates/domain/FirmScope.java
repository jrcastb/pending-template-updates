package com.example.pendingupdates.domain;

import java.util.Objects;

/**
 * The firm a caller is authorized for. Required by every repository call.
 *
 * <p>Must be derived from the authenticated token at the edge, never from a request body. In this
 * slice it is a plain record; the token-to-scope mapping is out of scope.
 */
public record FirmScope(FirmId firmId) {

    public FirmScope {
        Objects.requireNonNull(firmId, "firmId");
    }
}
