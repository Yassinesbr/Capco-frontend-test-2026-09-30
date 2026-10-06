package com.kyc.kycapplication.domain;

import java.util.Objects;

/** Privacy-safe reference to a required value that is not present. */
public record MissingRequiredField(ApplicationStep step, String field) {

    /** Rejects incomplete field references. */
    public MissingRequiredField {
        Objects.requireNonNull(step, "step must not be null");
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
    }
}
