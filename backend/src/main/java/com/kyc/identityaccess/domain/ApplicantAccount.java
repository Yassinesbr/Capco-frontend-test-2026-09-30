package com.kyc.identityaccess.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aggregate root for an applicant's account identity. */
public record ApplicantAccount(
        UUID id,
        EmailAddress email,
        PasswordVerifier passwordVerifier,
        Role role,
        Instant createdAt) {

    /** Validates account aggregate invariants. */
    public ApplicantAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordVerifier, "passwordVerifier must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (role != Role.APPLICANT) {
            throw new IllegalArgumentException("new accounts must use the applicant role");
        }
    }

    /** Registers an account with the only role this story may create. */
    public static ApplicantAccount register(
            final EmailAddress email,
            final PasswordVerifier passwordVerifier,
            final Instant createdAt) {
        return new ApplicantAccount(UUID.randomUUID(), email, passwordVerifier, Role.APPLICANT, createdAt);
    }
}
