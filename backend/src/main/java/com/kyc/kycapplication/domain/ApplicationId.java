package com.kyc.kycapplication.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque identity of a KYC application. */
public record ApplicationId(UUID value) {

    /** Rejects an absent application identity. */
    public ApplicationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /** Creates a new opaque application identity. */
    public static ApplicationId newId() {
        return new ApplicationId(UUID.randomUUID());
    }
}
