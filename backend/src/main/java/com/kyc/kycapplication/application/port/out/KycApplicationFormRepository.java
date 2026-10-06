package com.kyc.kycapplication.application.port.out;

import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import java.util.Optional;

/** Persistence boundary for sensitive Applicant form state. */
public interface KycApplicationFormRepository {

    /** Loads form state for an already-authorized application. */
    Optional<ApplicationForm> findByApplicationId(ApplicationId applicationId);

    /** Atomically persists form state and the application's current step. */
    void save(ApplicationForm form);
}
