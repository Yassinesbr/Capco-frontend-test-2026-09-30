package com.kyc.kycapplication.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the framework-free required-presence policy for the two-step form. */
class ApplicationFormTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private final ApplicationId applicationId = new ApplicationId(UUID.randomUUID());

    @Test
    void treatsEveryPersonalFieldAndConsentAsRequiredWithoutFormatRules() {
        ApplicationForm form = ApplicationForm.blank(applicationId, NOW);

        assertThat(form.missingRequiredFields()).extracting(MissingRequiredField::field)
                .containsExactly("name", "dateOfBirth", "country", "nationality", "email", "phone", "consentConfirmed",
                        "documentType", "documentNumber", "documentCountry", "expiry", "street", "city", "postal",
                        "residentialCountry", "documentEvidence");

        ApplicationForm updated = form.update(ApplicationStep.PERSONAL_DETAILS, Map.of(
                FormField.NAME, "1", FormField.DATE_OF_BIRTH, "not-a-date", FormField.COUNTRY, "x",
                FormField.NATIONALITY, "x", FormField.EMAIL, "not-an-email", FormField.PHONE, "x"), true, NOW.plusSeconds(1));

        assertThat(updated.currentStep()).isEqualTo(ApplicationStep.IDENTITY_AND_ADDRESS);
        assertThat(updated.progress()).extracting(StepProgress::state)
                .containsExactly(StepState.COMPLETE, StepState.CURRENT);
    }

    @Test
    void rejectsAnswersFromAnotherStep() {
        assertThatThrownBy(() -> ApplicationForm.blank(applicationId, NOW).update(
                ApplicationStep.PERSONAL_DETAILS, Map.of(FormField.CITY, "x"), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
