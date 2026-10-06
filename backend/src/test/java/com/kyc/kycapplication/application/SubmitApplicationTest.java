package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.ApplicationSubmissionRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicationAuditEventType;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStep;
import com.kyc.kycapplication.domain.ApplicationStatus;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.DocumentStorageReference;
import com.kyc.kycapplication.domain.FormField;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void rejectsIncompleteSubmissionWithoutPersistingAnAuditEvent() {
        Harness harness = new Harness(false);

        assertThatThrownBy(() -> harness.command.submit(harness.owner, harness.application.id(), 0))
                .isInstanceOf(com.kyc.kycapplication.domain.IncompleteApplicationException.class);

        assertThat(harness.submissionWrites).isZero();
        assertThat(harness.events).isEmpty();
    }

    @Test
    void commitsReadySubmissionAndOneMinimizedAuditEvent() {
        Harness harness = new Harness(true);

        SubmitApplication.SubmissionResult result = harness.command.submit(
                harness.owner, harness.application.id(), harness.form.version());

        assertThat(result.application().status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(result.application().submittedAt()).isEqualTo(NOW);
        assertThat(result.replayed()).isFalse();
        assertThat(harness.submissionWrites).isEqualTo(1);
        assertThat(harness.events).singleElement()
                .extracting(PrivacySafeApplicationAuditEvent::type)
                .isEqualTo(ApplicationAuditEventType.APPLICATION_SUBMITTED);
    }

    @Test
    void returnsThePersistedSubmissionOnReplayWithoutRepeatingSideEffects() {
        Harness harness = new Harness(true);
        harness.command.submit(harness.owner, harness.application.id(), harness.form.version());

        SubmitApplication.SubmissionResult replay = harness.command.submit(harness.owner, harness.application.id(), -1);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.application().submittedAt()).isEqualTo(NOW);
        assertThat(harness.submissionWrites).isEqualTo(1);
        assertThat(harness.events).hasSize(1);
    }

    @Test
    void deniesOtherApplicantsWithoutDisclosingTheApplication() {
        Harness harness = new Harness(true);
        ApplicantId anotherApplicant = new ApplicantId(UUID.randomUUID());

        assertThatThrownBy(() -> harness.command.submit(anotherApplicant, harness.application.id(), harness.form.version()))
                .isInstanceOf(ApplicationNotFoundException.class);

        assertThat(harness.submissionWrites).isZero();
    }

    @Test
    void rejectsAStaleFormVersionBeforePersistence() {
        Harness harness = new Harness(true);

        assertThatThrownBy(() -> harness.command.submit(harness.owner, harness.application.id(), harness.form.version() - 1))
                .isInstanceOf(FormVersionConflictException.class);

        assertThat(harness.submissionWrites).isZero();
    }

    private static ApplicationForm completeForm(final ApplicationId applicationId) {
        Map<FormField, String> personal = new EnumMap<>(FormField.class);
        FormField.answerFields(ApplicationStep.PERSONAL_DETAILS).forEach(field -> personal.put(field, "present"));
        ApplicationForm form = ApplicationForm.blank(applicationId, NOW)
                .update(ApplicationStep.PERSONAL_DETAILS, personal, true, NOW.plusSeconds(1));
        Map<FormField, String> identity = new EnumMap<>(FormField.class);
        FormField.answerFields(ApplicationStep.IDENTITY_AND_ADDRESS).forEach(field -> identity.put(field, "present"));
        form = form.update(ApplicationStep.IDENTITY_AND_ADDRESS, identity, null, NOW.plusSeconds(2));
        return form.withDocumentEvidence(
                new DocumentStorageReference("storage-key", "application/pdf", 10, "digest"), NOW.plusSeconds(3));
    }

    private final class Harness {
        private final ApplicantId owner = new ApplicantId(UUID.randomUUID());
        private final KycApplication application = KycApplication.start(owner, NOW.minusSeconds(10));
        private final ApplicationForm form;
        private final Map<ApplicationId, KycApplication> applications = new HashMap<>();
        private final Map<ApplicationId, ApplicationForm> forms = new HashMap<>();
        private final java.util.List<PrivacySafeApplicationAuditEvent> events = new java.util.ArrayList<>();
        private int submissionWrites;
        private final SubmitApplication command;

        private Harness(final boolean ready) {
            form = ready ? completeForm(application.id()) : ApplicationForm.blank(application.id(), NOW);
            applications.put(application.id(), application);
            forms.put(application.id(), form);
            KycApplicationRepository applicationRepository = new KycApplicationRepository() {
                @Override
                public Optional<KycApplication> findActiveByOwner(final ApplicantId ownerId) {
                    return applications.values().stream()
                            .filter(candidate -> candidate.ownerId().equals(ownerId))
                            .filter(candidate -> candidate.status() == ApplicationStatus.DRAFT).findFirst();
                }

                @Override
                public Optional<KycApplication> findById(final ApplicationId applicationId) {
                    return Optional.ofNullable(applications.get(applicationId));
                }

                @Override
                public void save(final KycApplication ignored) {
                    throw new UnsupportedOperationException();
                }
            };
            KycApplicationFormRepository formRepository = new KycApplicationFormRepository() {
                @Override
                public Optional<ApplicationForm> findByApplicationId(final ApplicationId applicationId) {
                    return Optional.ofNullable(forms.get(applicationId));
                }

                @Override
                public void save(final ApplicationForm ignored) {
                    throw new UnsupportedOperationException();
                }
            };
            ApplicationSubmissionRepository submissions = (submittedApplication, version, event) -> {
                submissionWrites++;
                applications.put(submittedApplication.id(), submittedApplication);
                events.add(event);
                return true;
            };
            command = new SubmitApplication(applicationRepository, formRepository, submissions,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
