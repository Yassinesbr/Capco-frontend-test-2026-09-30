package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.KycApplication;
import java.util.Objects;

/** Owner-scoped query for the Applicant form and review state. */
public final class GetApplicationForm {

    private final KycApplicationRepository applicationRepository;
    private final KycApplicationFormRepository formRepository;

    /** Creates the query from the existing application and form ports. */
    public GetApplicationForm(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository);
        this.formRepository = Objects.requireNonNull(formRepository);
    }

    /** Returns the current owner's form or a privacy-safe not-found result. */
    public ApplicationFormResult find(final ApplicantId ownerId, final ApplicationId applicationId) {
        KycApplication application = applicationRepository.findActiveByOwner(ownerId)
                .filter(candidate -> candidate.id().equals(applicationId))
                .orElseThrow(ApplicationNotFoundException::new);
        ApplicationForm form = formRepository.findByApplicationId(application.id())
                .orElseGet(() -> ApplicationForm.blank(application.id(), application.updatedAt()));
        return new ApplicationFormResult(application, form);
    }

    /** Form query result with the already-authorized application. */
    public record ApplicationFormResult(KycApplication application, ApplicationForm form) {
    }
}
