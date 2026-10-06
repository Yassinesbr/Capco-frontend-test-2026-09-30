package com.kyc.kycapplication.domain;

/** Deliberately non-specific denial for an application another Applicant does not own. */
public final class ApplicationOwnershipDeniedException extends RuntimeException {

    /** Creates the privacy-preserving ownership denial. */
    public ApplicationOwnershipDeniedException() {
        super("Application access denied.");
    }
}
