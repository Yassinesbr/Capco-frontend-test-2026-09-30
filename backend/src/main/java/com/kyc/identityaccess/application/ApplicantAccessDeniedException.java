package com.kyc.identityaccess.application;

/** Generic denial returned when a session cannot access a protected work area. */
public final class ApplicantAccessDeniedException extends RuntimeException {

    /** Creates a deliberately non-specific authorization denial. */
    public ApplicantAccessDeniedException() {
        super("Access denied.");
    }
}
