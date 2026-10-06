package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.Role;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Enforces the Applicant-only authority boundary for protected work areas. */
public final class AuthorizeApplicantAccess {

    private static final String ANONYMOUS_REQUEST = "anonymous-request";

    private final ApplicantSessionRepository sessionRepository;
    private final PrivacySafeAuditEventPublisher auditEventPublisher;
    private final Clock clock;

    /** Creates the authorization use case from session, audit, and time boundaries. */
    public AuthorizeApplicantAccess(
            final ApplicantSessionRepository sessionRepository,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Authorizes an active Applicant only for Applicant work. Reviewer and administrator requests
     * are denied server-side even when a caller has bypassed client navigation.
     */
    public AuthorizedApplicant authorize(final SessionId sessionId, final ProtectedWorkArea workArea) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workArea, "workArea must not be null");

        Instant now = clock.instant();
        Optional<ApplicantSession> session = sessionRepository.findActiveById(sessionId, now);
        if (session.isEmpty()) {
            publishDenial(ANONYMOUS_REQUEST, now);
            throw new ApplicantAccessDeniedException();
        }

        ApplicantSession activeSession = session.orElseThrow();
        boolean authorized = switch (workArea) {
            case APPLICANT -> activeSession.role() == Role.APPLICANT;
            case REVIEWER -> activeSession.role() == Role.REVIEWER || activeSession.role() == Role.ADMINISTRATOR;
            case ADMINISTRATOR -> activeSession.role() == Role.ADMINISTRATOR;
        };
        if (!authorized) {
            publishDenial(activeSession.applicantAccountId().toString(), now);
            throw new ApplicantAccessDeniedException();
        }
        return new AuthorizedApplicant(activeSession.applicantAccountId());
    }

    private void publishDenial(final String subjectReference, final Instant now) {
        auditEventPublisher.publish(new PrivacySafeAuditEvent(
                AuditEventType.AUTHORIZATION_DENIED,
                "denied",
                now,
                UUID.randomUUID(),
                subjectReference));
    }
}
