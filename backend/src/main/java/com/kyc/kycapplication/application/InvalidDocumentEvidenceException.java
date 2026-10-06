package com.kyc.kycapplication.application;

/** Safe client-actionable rejection for unsupported document evidence. */
public final class InvalidDocumentEvidenceException extends RuntimeException {

    /** Creates a safe rejection without file details. */
    public InvalidDocumentEvidenceException(final String message) {
        super(message);
    }
}
