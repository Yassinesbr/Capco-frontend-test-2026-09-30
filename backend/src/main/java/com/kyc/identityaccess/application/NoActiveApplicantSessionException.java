package com.kyc.identityaccess.application;

/** Signals that the supplied opaque session no longer authorizes an Applicant. */
public final class NoActiveApplicantSessionException extends RuntimeException {

    /** Creates the public unauthenticated-session outcome. */
    public NoActiveApplicantSessionException() {
        super("No active applicant session");
    }
}
