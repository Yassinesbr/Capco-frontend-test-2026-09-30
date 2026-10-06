package com.kyc.kycapplication.domain;

/** Indicates that an application lifecycle transition is not valid. */
public final class InvalidApplicationLifecycleException extends RuntimeException {

    public InvalidApplicationLifecycleException() {
        super("The application cannot be submitted in its current state.");
    }
}
