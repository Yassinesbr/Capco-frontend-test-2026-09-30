package com.kyc.kycapplication.domain;

import java.util.List;

/** Indicates that required application values are missing at submission time. */
public final class IncompleteApplicationException extends RuntimeException {

    private final List<MissingRequiredField> missingFields;

    public IncompleteApplicationException(final List<MissingRequiredField> missingFields) {
        super("Required application information is missing.");
        this.missingFields = List.copyOf(missingFields);
    }

    /** Returns safe field references only, without submitted answer values. */
    public List<MissingRequiredField> missingFields() {
        return missingFields;
    }
}
