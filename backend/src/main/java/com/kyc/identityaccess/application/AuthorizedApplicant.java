package com.kyc.identityaccess.application;

import java.util.Objects;
import java.util.UUID;

/** The minimal identity returned after an Applicant-area authorization check. */
public record AuthorizedApplicant(UUID accountId) {

    /** Rejects an absent account identity. */
    public AuthorizedApplicant {
        Objects.requireNonNull(accountId, "accountId must not be null");
    }
}
