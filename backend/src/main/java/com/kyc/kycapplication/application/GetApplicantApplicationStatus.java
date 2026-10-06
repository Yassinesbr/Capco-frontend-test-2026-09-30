package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationOwnershipDeniedException;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.KycApplication;
import java.util.Objects;

/** Returns only an authenticated Applicant's minimized lifecycle state. */
public final class GetApplicantApplicationStatus {

    private final KycApplicationRepository repository;

    public GetApplicantApplicationStatus(final KycApplicationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public KycApplication find(final ApplicantId ownerId, final ApplicationId applicationId) {
        KycApplication application = repository.findById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        try {
            application.requireOwner(ownerId);
        } catch (ApplicationOwnershipDeniedException exception) {
            throw new ApplicationNotFoundException();
        }
        return application;
    }
}
