package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.ApplicationSubmissionRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicationAuditEventType;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationVersionConflictException;
import com.kyc.kycapplication.domain.ApplicationOwnershipDeniedException;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.IncompleteApplicationException;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Confirms an owner-scoped ready application and commits its lifecycle exactly once. */
public final class SubmitApplication {

    private final KycApplicationRepository applicationRepository;
    private final KycApplicationFormRepository formRepository;
    private final ApplicationSubmissionRepository submissionRepository;
    private final Clock clock;

    public SubmitApplication(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final ApplicationSubmissionRepository submissionRepository,
            final Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository);
        this.formRepository = Objects.requireNonNull(formRepository);
        this.submissionRepository = Objects.requireNonNull(submissionRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Commits submission or returns the already-persisted result after a replay. */
    public SubmissionResult submit(
            final ApplicantId ownerId,
            final ApplicationId applicationId,
            final int expectedFormVersion) {
        KycApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        requireVisibleTo(application, ownerId);
        ApplicationForm form = formRepository.findByApplicationId(applicationId)
                .orElseGet(() -> ApplicationForm.blank(applicationId, application.updatedAt()));
        if (application.status() == com.kyc.kycapplication.domain.ApplicationStatus.SUBMITTED) {
            return new SubmissionResult(application, form, true);
        }

        final Instant submittedAt = Instant.now(clock);
        final KycApplication submitted;
        try {
            submitted = application.submit(ownerId, form, expectedFormVersion, submittedAt);
        } catch (ApplicationVersionConflictException exception) {
            throw new FormVersionConflictException();
        } catch (IncompleteApplicationException exception) {
            throw exception;
        }
        PrivacySafeApplicationAuditEvent auditEvent = new PrivacySafeApplicationAuditEvent(
                ApplicationAuditEventType.APPLICATION_SUBMITTED,
                "success",
                submittedAt,
                UUID.randomUUID(),
                UUID.nameUUIDFromBytes(applicationId.value().toString().getBytes(StandardCharsets.UTF_8)).toString());
        if (submissionRepository.commitSubmission(submitted, expectedFormVersion, auditEvent)) {
            return new SubmissionResult(submitted, form, false);
        }

        KycApplication current = applicationRepository.findById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        requireVisibleTo(current, ownerId);
        if (current.status() == com.kyc.kycapplication.domain.ApplicationStatus.SUBMITTED) {
            ApplicationForm committedForm = formRepository.findByApplicationId(applicationId).orElse(form);
            return new SubmissionResult(current, committedForm, true);
        }
        throw new FormVersionConflictException();
    }

    private static void requireVisibleTo(final KycApplication application, final ApplicantId ownerId) {
        try {
            application.requireOwner(ownerId);
        } catch (ApplicationOwnershipDeniedException exception) {
            throw new ApplicationNotFoundException();
        }
    }

    /** Persisted application, authoritative form, and whether this was a replay. */
    public record SubmissionResult(KycApplication application, ApplicationForm form, boolean replayed) {
    }
}
