package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.application.ApplicantJourney;
import java.util.UUID;

/** Read-only in-process boundary to the current journey owned by KYC Application. */
public interface CurrentApplicantJourney {

    /** Finds the current journey destination for an authenticated Applicant. */
    ApplicantJourney findFor(UUID applicantAccountId);
}
