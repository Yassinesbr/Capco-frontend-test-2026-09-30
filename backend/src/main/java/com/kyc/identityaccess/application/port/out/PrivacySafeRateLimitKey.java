package com.kyc.identityaccess.application.port.out;

import java.util.Objects;

/** Hashed or otherwise minimized subject/network signal used only for throttling. */
public record PrivacySafeRateLimitKey(String value) {

    /** Rejects blank rate-limit keys. */
    public PrivacySafeRateLimitKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
