package com.kyc.kycapplication.application.port.out;

import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.ApplicationId;
import java.util.Optional;

/** Persistence boundary for active Applicant-owned KYC drafts. */
public interface KycApplicationRepository {

    /** Finds the Applicant's sole active draft when one exists. */
    Optional<KycApplication> findActiveByOwner(ApplicantId ownerId);

    /** Finds an application by identifier for an owner-checked Applicant operation. */
    Optional<KycApplication> findById(ApplicationId applicationId);

    /** Persists a new draft and may reject a concurrent active-draft creation. */
    void save(KycApplication application);
}
