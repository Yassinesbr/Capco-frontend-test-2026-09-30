package com.kyc.kycapplication.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KycApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void startsAtPersonalDetailsWithOrderedTruthfulProgress() {
        KycApplication application = KycApplication.start(applicant(), NOW);

        assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(application.currentStep()).isEqualTo(ApplicationStep.PERSONAL_DETAILS);
        assertThat(application.progress()).containsExactly(
                new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.CURRENT),
                new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS, StepState.NOT_STARTED));
    }

    @Test
    void identityAndAddressCanOnlyRepresentTheFirstStepAsComplete() {
        KycApplication application = new KycApplication(
                ApplicationId.newId(), applicant(), ApplicationStatus.DRAFT,
                ApplicationStep.IDENTITY_AND_ADDRESS, NOW, NOW);

        assertThat(application.progress()).containsExactly(
                new StepProgress(ApplicationStep.PERSONAL_DETAILS, StepState.COMPLETE),
                new StepProgress(ApplicationStep.IDENTITY_AND_ADDRESS, StepState.CURRENT));
        assertThat(application.progress()).noneMatch(progress -> progress.state() == StepState.COMPLETE
                && progress.step() == ApplicationStep.IDENTITY_AND_ADDRESS);
    }

    @Test
    void rejectsAnotherApplicantWithoutDisclosingTheApplicationState() {
        KycApplication application = KycApplication.start(applicant(), NOW);

        assertThatThrownBy(() -> application.requireOwner(new ApplicantId(UUID.randomUUID())))
                .isInstanceOf(ApplicationOwnershipDeniedException.class)
                .hasMessage("Application access denied.");
    }

    @Test
    void rejectsSubmissionWhenRequiredFieldsAreMissing() {
        KycApplication application = KycApplication.start(applicant(), NOW);
        ApplicationForm form = ApplicationForm.blank(application.id(), NOW);

        assertThatThrownBy(() -> application.submit(applicant(), form, 0, NOW.plusSeconds(1)))
                .isInstanceOf(IncompleteApplicationException.class)
                .satisfies(exception -> assertThat(((IncompleteApplicationException) exception).missingFields())
                        .contains(new MissingRequiredField(ApplicationStep.PERSONAL_DETAILS, "name")));
    }

    @Test
    void transitionsReadyDraftToSubmittedWithTimestamp() {
        KycApplication application = KycApplication.start(applicant(), NOW);
        ApplicationForm form = completeForm(application.id());
        Instant submittedAt = NOW.plusSeconds(2);

        KycApplication submitted = application.submit(applicant(), form, form.version(), submittedAt);

        assertThat(submitted.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isEqualTo(submittedAt);
        assertThat(submitted.updatedAt()).isEqualTo(submittedAt);
    }

    @Test
    void rejectsSubmissionWhenTheReviewedFormVersionIsStale() {
        KycApplication application = KycApplication.start(applicant(), NOW);
        ApplicationForm form = completeForm(application.id());

        assertThatThrownBy(() -> application.submit(applicant(), form, form.version() - 1, NOW.plusSeconds(1)))
                .isInstanceOf(ApplicationVersionConflictException.class);
    }

    @Test
    void treatsSubmissionReplayAsTheSamePersistedTransition() {
        KycApplication application = KycApplication.start(applicant(), NOW);
        ApplicationForm form = completeForm(application.id());
        KycApplication submitted = application.submit(applicant(), form, form.version(), NOW.plusSeconds(1));

        KycApplication replay = submitted.submit(applicant(), form, -1, NOW.plusSeconds(5));

        assertThat(replay).isSameAs(submitted);
        assertThat(replay.submittedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectsSubmittedStateWithoutSubmissionTimestamp() {
        assertThatThrownBy(() -> new KycApplication(
                ApplicationId.newId(), applicant(), ApplicationStatus.SUBMITTED,
                ApplicationStep.IDENTITY_AND_ADDRESS, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationForm completeForm(final ApplicationId applicationId) {
        Map<FormField, String> answers = new EnumMap<>(FormField.class);
        for (FormField field : FormField.answerFields(ApplicationStep.PERSONAL_DETAILS)) {
            answers.put(field, "present");
        }
        ApplicationForm form = ApplicationForm.blank(applicationId, NOW)
                .update(ApplicationStep.PERSONAL_DETAILS, Map.copyOf(answers), true, NOW.plusSeconds(1));
        Map<FormField, String> identityAnswers = new EnumMap<>(FormField.class);
        for (FormField field : FormField.answerFields(ApplicationStep.IDENTITY_AND_ADDRESS)) {
            identityAnswers.put(field, "present");
        }
        form = form.update(ApplicationStep.IDENTITY_AND_ADDRESS, identityAnswers, null, NOW.plusSeconds(2));
        return form.withDocumentEvidence(
                new DocumentStorageReference("evidence-key", "application/pdf", 1, "digest"), NOW.plusSeconds(3));
    }

    private static ApplicantId applicant() {
        return new ApplicantId(UUID.fromString("0a6c7d5e-6d4e-4b67-a7f8-3f67a6249b5e"));
    }
}
