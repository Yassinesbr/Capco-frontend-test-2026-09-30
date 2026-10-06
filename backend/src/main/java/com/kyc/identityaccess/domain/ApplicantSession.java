package com.kyc.identityaccess.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Time-bounded authority for a signed-in user. */
public record ApplicantSession(
        SessionId id,
        UUID applicantAccountId,
        Role role,
        Instant createdAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        Instant revokedAt) {

    /** Validates session authority and lifetime. */
    public ApplicantSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applicantAccountId, "applicantAccountId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(idleExpiresAt, "idleExpiresAt must not be null");
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
        if (!idleExpiresAt.isAfter(createdAt) || !absoluteExpiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("session expiry must follow creation");
        }
    }

    /** Returns whether the session can authorize an Applicant at the supplied instant. */
    public boolean isActiveAt(final Instant instant) {
        return revokedAt == null
                && instant.isBefore(idleExpiresAt)
                && instant.isBefore(absoluteExpiresAt);
    }

    /** Returns a revoked copy of this session. */
    public ApplicantSession revoke(final Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        return new ApplicantSession(
                id,
                applicantAccountId,
                role,
                createdAt,
                idleExpiresAt,
                absoluteExpiresAt,
                revokedAt);
    }
}
