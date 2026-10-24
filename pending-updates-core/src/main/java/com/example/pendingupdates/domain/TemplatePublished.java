package com.example.pendingupdates.domain;

import java.util.Objects;

/** Event from the template store's publish hook. Template data only: shared by all firms. */
public record TemplatePublished(TemplateId templateId, Version version, String publishId) {

    public TemplatePublished {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(publishId, "publishId");
        if (publishId.isBlank()) {
            throw new IllegalArgumentException("publish id must not be blank");
        }
    }
}
