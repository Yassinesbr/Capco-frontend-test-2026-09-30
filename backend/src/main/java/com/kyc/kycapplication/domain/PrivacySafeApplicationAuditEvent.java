package com.kyc.kycapplication.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Operational fact that intentionally excludes KYC answers, routes, and application identifiers. */
public record PrivacySafeApplicationAuditEvent(
        ApplicationAuditEventType type,
        String outcome,
        Instant occurredAt,
        UUID correlationId,
        String minimizedSubjectReference) {

    /** Rejects missing and unsafe audit fields. */
    public PrivacySafeApplicationAuditEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(minimizedSubjectReference, "minimizedSubjectReference must not be null");
        if (outcome.isBlank() || minimizedSubjectReference.isBlank() || minimizedSubjectReference.contains("/")) {
            throw new IllegalArgumentException("audit fields must be minimized");
        }
    }
}
