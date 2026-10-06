package com.kyc.identityaccess.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Audit fact deliberately limited to non-secret correlation data. */
public record PrivacySafeAuditEvent(
        AuditEventType type,
        String outcome,
        Instant occurredAt,
        UUID correlationId,
        String minimizedSubjectReference) {

    private static final Set<String> FIXED_NON_SECRET_REFERENCES =
            Set.of("credential-attempt", "anonymous-request");

    /** Rejects absent audit metadata and raw-secret shaped payloads. */
    public PrivacySafeAuditEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(minimizedSubjectReference, "minimizedSubjectReference must not be null");
        if (outcome.isBlank() || minimizedSubjectReference.isBlank()) {
            throw new IllegalArgumentException("audit values must not be blank");
        }
        if (!FIXED_NON_SECRET_REFERENCES.contains(minimizedSubjectReference)
                && !isUuid(minimizedSubjectReference)) {
            throw new IllegalArgumentException("audit subject reference must be a safe fixed label or account ID");
        }
    }

    private static boolean isUuid(final String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
