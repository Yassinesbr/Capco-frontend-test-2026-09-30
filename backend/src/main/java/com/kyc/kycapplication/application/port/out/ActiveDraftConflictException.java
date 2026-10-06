package com.kyc.kycapplication.application.port.out;

/** Signals that persistence already holds the owner's sole active draft. */
public final class ActiveDraftConflictException extends RuntimeException {

    /** Creates the persistence conflict signal without exposing database details. */
    public ActiveDraftConflictException(final Throwable cause) {
        super("An active draft already exists.", cause);
    }
}
