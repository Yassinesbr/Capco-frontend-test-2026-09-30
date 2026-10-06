package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Revokes an active Applicant session and records the privacy-safe outcome. */
public final class SignOutApplicant {

    private final ApplicantSessionRepository sessionRepository;
    private final PrivacySafeAuditEventPublisher auditEventPublisher;
    private final Clock clock;

    /** Creates the sign-out use case from its session, audit, and time boundaries. */
    public SignOutApplicant(
            final ApplicantSessionRepository sessionRepository,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Revokes the active supplied session, so it cannot authorize a later request. */
    public void signOut(final SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Instant now = clock.instant();
        ApplicantSession session = sessionRepository
                .findActiveById(sessionId, now)
                .orElseThrow(NoActiveApplicantSessionException::new);
        sessionRepository.revoke(sessionId, now);
        auditEventPublisher.publish(new PrivacySafeAuditEvent(
                AuditEventType.APPLICANT_SIGNED_OUT,
                "success",
                now,
                UUID.randomUUID(),
                session.applicantAccountId().toString()));
    }
}
