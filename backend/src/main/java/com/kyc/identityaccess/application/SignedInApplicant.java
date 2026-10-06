package com.kyc.identityaccess.application;

import com.kyc.identityaccess.domain.SessionId;
import java.util.Objects;

/** The newly rotated session and destination produced by successful Applicant authentication. */
public record SignedInApplicant(SessionId sessionId, ApplicantJourney journey) {

    /** Rejects incomplete successful-authentication results. */
    public SignedInApplicant {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(journey, "journey must not be null");
    }
}
