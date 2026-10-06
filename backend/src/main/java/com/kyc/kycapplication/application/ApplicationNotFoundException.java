package com.kyc.kycapplication.application;

/** Privacy-safe absence of an owner-scoped active application. */
public final class ApplicationNotFoundException extends RuntimeException {

    /** Creates the non-disclosing exception. */
    public ApplicationNotFoundException() {
        super("Application not found.");
    }
}
