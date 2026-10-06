package com.kyc.kycapplication.adapter.web;

/** Signals that an Applicant journey request had no usable authenticated session. */
public final class ApplicantAuthenticationRequiredException extends RuntimeException {

    /** Creates the generic authentication-required outcome. */
    public ApplicantAuthenticationRequiredException() {
        super("Authentication required.");
    }
}
