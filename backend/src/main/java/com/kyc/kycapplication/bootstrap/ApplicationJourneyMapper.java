package com.kyc.kycapplication.bootstrap;

import com.kyc.kycapplication.domain.KycApplication;

/** Maps an aggregate's earliest incomplete step to its local Applicant route. */
public final class ApplicationJourneyMapper {

    private ApplicationJourneyMapper() {
    }

    /** Builds the local route for the Application's earliest incomplete step. */
    public static String href(final KycApplication application) {
        String segment = switch (application.currentStep()) {
            case PERSONAL_DETAILS -> "personal-details";
            case IDENTITY_AND_ADDRESS -> "identity-and-address";
        };
        return "/applications/" + application.id().value() + "/" + segment;
    }
}
