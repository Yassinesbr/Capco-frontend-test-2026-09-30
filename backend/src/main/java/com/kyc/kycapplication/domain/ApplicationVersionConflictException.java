package com.kyc.kycapplication.domain;

/** Indicates that the Applicant confirmed a form version that is no longer current. */
public final class ApplicationVersionConflictException extends RuntimeException {

    public ApplicationVersionConflictException() {
        super("The application changed before it could be submitted.");
    }
}
