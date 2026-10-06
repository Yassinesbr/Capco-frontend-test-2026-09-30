package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStep;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.FormField;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Saves one owner-scoped step while keeping answer and progress state atomic. */
public final class SaveApplicationForm {

    private final GetApplicationForm getApplicationForm;
    private final KycApplicationFormRepository formRepository;
    private final Clock clock;

    /** Creates the save use case. */
    public SaveApplicationForm(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final Clock clock) {
        this.getApplicationForm = new GetApplicationForm(applicationRepository, formRepository);
        this.formRepository = Objects.requireNonNull(formRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Applies the selected step and returns the resulting state. */
    public GetApplicationForm.ApplicationFormResult save(
            final ApplicantId ownerId,
            final ApplicationId applicationId,
            final ApplicationStep step,
            final Map<FormField, String> answers,
            final Boolean consentConfirmed,
            final Integer expectedVersion) {
        GetApplicationForm.ApplicationFormResult current = getApplicationForm.find(ownerId, applicationId);
        ApplicationForm form = current.form();
        if (expectedVersion != null && expectedVersion != form.version()) {
            throw new FormVersionConflictException();
        }
        ApplicationForm updated = form.update(step, answers, consentConfirmed, Instant.now(clock));
        formRepository.save(updated);
        return new GetApplicationForm.ApplicationFormResult(current.application(), updated);
    }
}
