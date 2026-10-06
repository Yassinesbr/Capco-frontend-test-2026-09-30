package com.kyc.identityaccess.domain;

import java.util.Objects;

/** Opaque server-side session identifier. */
public record SessionId(String value) {

    /** Rejects absent or blank session identifiers. */
    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("session identifier must not be blank");
        }
    }
}
