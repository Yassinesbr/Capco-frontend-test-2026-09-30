package com.kyc.identityaccess.application;

import java.util.Objects;

/** Public destination supplied by the KYC Application bounded context after sign-in. */
public record ApplicantJourney(String href) {

    /** Restricts a journey destination to an application-local absolute path. */
    public ApplicantJourney {
        Objects.requireNonNull(href, "href must not be null");
        if (href.isBlank() || !href.startsWith("/") || href.startsWith("//")) {
            throw new IllegalArgumentException("journey href must be an application-local path");
        }
    }
}
