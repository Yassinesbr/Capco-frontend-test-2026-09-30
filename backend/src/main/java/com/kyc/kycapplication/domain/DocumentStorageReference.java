package com.kyc.kycapplication.domain;

import java.util.Objects;

/** Private reference to accepted document evidence, never returned in Applicant responses. */
public record DocumentStorageReference(String storageKey, String mediaType, long size, String digest) {

    public DocumentStorageReference {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
