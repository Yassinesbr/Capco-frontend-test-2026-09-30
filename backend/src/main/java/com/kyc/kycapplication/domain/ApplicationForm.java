package com.kyc.kycapplication.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Framework-free Applicant form state and required-presence policy. */
public final class ApplicationForm {

    private final ApplicationId applicationId;
    private final Map<FormField, String> answers;
    private final boolean consentConfirmed;
    private final boolean documentEvidencePresent;
    private final String documentStorageKey;
    private final String documentMediaType;
    private final Long documentSize;
    private final String documentDigest;
    private final int version;
    private final Instant updatedAt;

    private ApplicationForm(
            final ApplicationId applicationId,
            final Map<FormField, String> answers,
            final boolean consentConfirmed,
            final boolean documentEvidencePresent,
            final String documentStorageKey,
            final String documentMediaType,
            final Long documentSize,
            final String documentDigest,
            final int version,
            final Instant updatedAt) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        this.consentConfirmed = consentConfirmed;
        this.documentEvidencePresent = documentEvidencePresent;
        this.documentStorageKey = documentStorageKey;
        this.documentMediaType = documentMediaType;
        this.documentSize = documentSize;
        this.documentDigest = documentDigest;
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** Creates an empty form for a draft application. */
    public static ApplicationForm blank(final ApplicationId applicationId, final Instant now) {
        return new ApplicationForm(applicationId, Map.of(), false, false, null, null, null, null, 0, now);
    }

    /** Rehydrates a persisted form. */
    public static ApplicationForm rehydrate(
            final ApplicationId applicationId,
            final Map<FormField, String> answers,
            final boolean consentConfirmed,
            final boolean documentEvidencePresent,
            final int version,
            final Instant updatedAt) {
        return rehydrate(applicationId, answers, consentConfirmed, documentEvidencePresent,
                null, null, null, null, version, updatedAt);
    }

    /** Rehydrates a persisted form including private document metadata. */
    public static ApplicationForm rehydrate(
            final ApplicationId applicationId,
            final Map<FormField, String> answers,
            final boolean consentConfirmed,
            final boolean documentEvidencePresent,
            final String documentStorageKey,
            final String documentMediaType,
            final Long documentSize,
            final String documentDigest,
            final int version,
            final Instant updatedAt) {
        return new ApplicationForm(
                applicationId, normalize(answers), consentConfirmed, documentEvidencePresent,
                documentStorageKey, documentMediaType, documentSize, documentDigest, version, updatedAt);
    }

    /** Applies only fields belonging to the selected step and advances the version. */
    public ApplicationForm update(
            final ApplicationStep step,
            final Map<FormField, String> submittedAnswers,
            final Boolean submittedConsent,
            final Instant now) {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(submittedAnswers, "submittedAnswers must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!FormField.answerFields(step).containsAll(submittedAnswers.keySet())) {
            throw new IllegalArgumentException("submitted answer does not belong to the selected step");
        }
        Map<FormField, String> nextAnswers = new LinkedHashMap<>(answers);
        submittedAnswers.forEach((field, value) -> putOrRemove(nextAnswers, field, value));
        boolean nextConsent = consentConfirmed;
        if (submittedConsent != null) {
            nextConsent = submittedConsent;
        }
        return new ApplicationForm(applicationId, nextAnswers, nextConsent, documentEvidencePresent,
                documentStorageKey, documentMediaType, documentSize, documentDigest, version + 1, now);
    }

    /** Records that the required document evidence was stored. */
    public ApplicationForm withDocumentEvidence(
            final DocumentStorageReference document, final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(document, "document must not be null");
        return new ApplicationForm(applicationId, answers, consentConfirmed, true,
                document.storageKey(), document.mediaType(), document.size(), document.digest(), version + 1, now);
    }

    /** Returns all missing required fields in deterministic step/field order. */
    public List<MissingRequiredField> missingRequiredFields() {
        List<MissingRequiredField> missing = new ArrayList<>();
        for (ApplicationStep step : ApplicationStep.values()) {
            for (FormField field : FormField.values()) {
                if (field.step() != step) {
                    continue;
                }
                if (field == FormField.CONSENT && !consentConfirmed) {
                    missing.add(new MissingRequiredField(step, field.wireName()));
                } else if (field == FormField.DOCUMENT_EVIDENCE && !documentEvidencePresent) {
                    missing.add(new MissingRequiredField(step, field.wireName()));
                } else if (field != FormField.CONSENT
                        && field != FormField.DOCUMENT_EVIDENCE
                        && isBlank(answers.get(field))) {
                    missing.add(new MissingRequiredField(step, field.wireName()));
                }
            }
        }
        return List.copyOf(missing);
    }

    /** Returns the earliest incomplete step, or the final step when the draft is ready. */
    public ApplicationStep currentStep() {
        return missingFor(ApplicationStep.PERSONAL_DETAILS).isEmpty()
                ? ApplicationStep.IDENTITY_AND_ADDRESS : ApplicationStep.PERSONAL_DETAILS;
    }

    /** Returns whether all required fields, including document evidence, are present. */
    public boolean ready() {
        return missingRequiredFields().isEmpty();
    }

    /** Returns truthful progress for the two ordered steps. */
    public List<StepProgress> progress() {
        boolean personalComplete = missingFor(ApplicationStep.PERSONAL_DETAILS).isEmpty();
        boolean identityComplete = missingFor(ApplicationStep.IDENTITY_AND_ADDRESS).isEmpty();
        if (!personalComplete) {
            return List.of(
                    new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.CURRENT),
                    new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS, StepState.NOT_STARTED));
        }
        return List.of(
                new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.COMPLETE),
                new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS,
                        identityComplete ? StepState.COMPLETE : StepState.CURRENT));
    }

    /** Returns values submitted for transport without exposing internal mutability. */
    public Map<FormField, String> answers() {
        return answers;
    }

    public ApplicationId applicationId() {
        return applicationId;
    }

    public boolean consentConfirmed() {
        return consentConfirmed;
    }

    public boolean documentEvidencePresent() {
        return documentEvidencePresent;
    }

    public int version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String documentStorageKey() {
        return documentStorageKey;
    }

    public String documentMediaType() {
        return documentMediaType;
    }

    public Long documentSize() {
        return documentSize;
    }

    public String documentDigest() {
        return documentDigest;
    }

    private List<MissingRequiredField> missingFor(final ApplicationStep step) {
        return missingRequiredFields().stream().filter(field -> field.step() == step).toList();
    }

    private static Map<FormField, String> normalize(final Map<FormField, String> answers) {
        Map<FormField, String> normalized = new LinkedHashMap<>();
        answers.forEach((field, value) -> putOrRemove(normalized, field, value));
        return normalized;
    }

    private static void putOrRemove(final Map<FormField, String> answers, final FormField field, final String value) {
        Objects.requireNonNull(field, "field must not be null");
        if (isBlank(value)) {
            answers.remove(field);
        } else {
            answers.put(field, value.trim());
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
