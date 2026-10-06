package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.ActiveDraftConflictException;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.application.port.out.PrivacySafeApplicationAuditPublisher;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationAuditEventType;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Idempotently starts the Applicant's sole active KYC draft. */
public final class StartApplication {

    private final KycApplicationRepository repository;
    private final PrivacySafeApplicationAuditPublisher auditPublisher;
    private final Clock clock;

    /** Creates the start command from the aggregate repository and a clock. */
    public StartApplication(
            final KycApplicationRepository repository,
            final PrivacySafeApplicationAuditPublisher auditPublisher,
            final Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Returns a newly created draft or the existing owned draft when start is repeated or races. */
    public StartResult start(final ApplicantId ownerId) {
        return repository.findActiveByOwner(ownerId)
                .map(application -> observed(application, false))
                .orElseGet(() -> createOrRecover(ownerId));
    }

    private StartResult createOrRecover(final ApplicantId ownerId) {
        KycApplication application = KycApplication.start(ownerId, clock.instant());
        try {
            repository.save(application);
            return observed(application, true);
        } catch (ActiveDraftConflictException exception) {
            KycApplication existing = repository.findActiveByOwner(ownerId).orElseThrow(() -> exception);
            return observed(existing, false);
        }
    }

    private StartResult observed(final KycApplication application, final boolean created) {
        auditPublisher.publish(new PrivacySafeApplicationAuditEvent(
                created ? ApplicationAuditEventType.APPLICATION_STARTED : ApplicationAuditEventType.APPLICATION_RESUMED,
                "success",
                clock.instant(),
                UUID.randomUUID(),
                application.ownerId().value().toString()));
        return new StartResult(application, created);
    }

    /** Reports both the Applicant-owned draft and whether this command created it. */
    public record StartResult(KycApplication application, boolean created) {
    }
}
