package com.kyc.kycapplication.domain;

import java.util.Objects;
import java.util.UUID;

/** Anti-corruption representation of an Applicant identity supplied by Identity and Access. */
public record ApplicantId(UUID value) {

    /** Rejects an absent Applicant identity. */
    public ApplicantId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
