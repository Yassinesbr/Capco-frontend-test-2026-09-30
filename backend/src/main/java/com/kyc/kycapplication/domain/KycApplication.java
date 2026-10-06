package com.kyc.kycapplication.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Applicant-owned aggregate that protects the truthful draft lifecycle. */
public record KycApplication(
        ApplicationId id,
        ApplicantId ownerId,
        ApplicationStatus status,
        ApplicationStep currentStep,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant approvedAt) {

    /** Keeps prior persistence construction compatible while approval remains optional lifecycle data. */
    public KycApplication(
            final ApplicationId id,
            final ApplicantId ownerId,
            final ApplicationStatus status,
            final ApplicationStep currentStep,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant submittedAt) {
        this(id, ownerId, status, currentStep, createdAt, updatedAt, submittedAt, null);
    }

    /** Keeps existing draft construction explicit while submission timestamps remain lifecycle data. */
    public KycApplication(
            final ApplicationId id,
            final ApplicantId ownerId,
            final ApplicationStatus status,
            final ApplicationStep currentStep,
            final Instant createdAt,
            final Instant updatedAt) {
        this(id, ownerId, status, currentStep, createdAt, updatedAt, null);
    }

    /** Validates the draft aggregate invariants owned by this ticket. */
    public KycApplication {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentStep, "currentStep must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == ApplicationStatus.DRAFT && (submittedAt != null || approvedAt != null)) {
            throw new IllegalArgumentException("draft application must not have lifecycle timestamps");
        }
        if (status != ApplicationStatus.DRAFT
                && (submittedAt == null || submittedAt.isBefore(createdAt) || submittedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("submitted application must have a valid submittedAt");
        }
        if (status == ApplicationStatus.APPROVED
                && (approvedAt == null || approvedAt.isBefore(submittedAt) || approvedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("approved application must have a valid approvedAt");
        }
        if (status != ApplicationStatus.APPROVED && approvedAt != null) {
            throw new IllegalArgumentException("only approved applications may have approvedAt");
        }
    }

    /** Starts a Draft at the first incomplete step without claiming any completion. */
    public static KycApplication start(final ApplicantId ownerId, final Instant now) {
        return new KycApplication(
                ApplicationId.newId(), ownerId, ApplicationStatus.DRAFT, ApplicationStep.PERSONAL_DETAILS, now, now);
    }

    /** Returns the ordered, truthful progress representation for the current draft. */
    public List<StepProgress> progress() {
        return switch (currentStep) {
            case PERSONAL_DETAILS -> List.of(
                    new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.CURRENT),
                    new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS, StepState.NOT_STARTED));
            case IDENTITY_AND_ADDRESS -> List.of(
                    new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.COMPLETE),
                    new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS, StepState.CURRENT));
        };
    }

    /** Rejects access from an identity other than this application's owner. */
    public void requireOwner(final ApplicantId applicantId) {
        if (!ownerId.equals(Objects.requireNonNull(applicantId, "applicantId must not be null"))) {
            throw new ApplicationOwnershipDeniedException();
        }
    }

    /** Applies the guarded, replay-safe lifecycle transition after checking the authoritative form. */
    public KycApplication submit(
            final ApplicantId applicantId,
            final ApplicationForm form,
            final int expectedFormVersion,
            final Instant now) {
        requireOwner(applicantId);
        Objects.requireNonNull(form, "form must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!id.equals(form.applicationId())) {
            throw new IllegalArgumentException("form must belong to this application");
        }
        if (status == ApplicationStatus.SUBMITTED) {
            return this;
        }
        if (status != ApplicationStatus.DRAFT) {
            throw new InvalidApplicationLifecycleException();
        }
        if (expectedFormVersion != form.version()) {
            throw new ApplicationVersionConflictException();
        }
        if (!form.ready()) {
            throw new IncompleteApplicationException(form.missingRequiredFields());
        }
        return new KycApplication(id, ownerId, ApplicationStatus.SUBMITTED, currentStep, createdAt, now, now);
    }
}
