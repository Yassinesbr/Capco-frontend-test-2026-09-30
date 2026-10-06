package com.kyc.identityaccess.domain;

import java.util.Objects;

/** A one-way password verifier produced by a hashing adapter. */
public record PasswordVerifier(String encodedValue) {

    /** Rejects blank verifier values. */
    public PasswordVerifier {
        Objects.requireNonNull(encodedValue, "encodedValue must not be null");
        if (encodedValue.isBlank()) {
            throw new IllegalArgumentException("encodedValue must not be blank");
        }
    }
}
