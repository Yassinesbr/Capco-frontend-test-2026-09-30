package com.kyc.kycapplication.application;

/** Signals that an autosave was based on an older form version. */
public final class FormVersionConflictException extends RuntimeException {

    /** Creates the conflict exception. */
    public FormVersionConflictException() {
        super("The form has changed. Reload and try again.");
    }
}
