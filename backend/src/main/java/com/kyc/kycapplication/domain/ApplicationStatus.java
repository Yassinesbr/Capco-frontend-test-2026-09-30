package com.kyc.kycapplication.domain;

/** Lifecycle states owned by KYC Application. */
public enum ApplicationStatus {
    /** The unsubmitted and resumable lifecycle state. */
    DRAFT,
    /** The final Applicant lifecycle state accepted for later review. */
    SUBMITTED,
    /** The application is being reviewed by staff. */
    IN_REVIEW,
    /** Staff approved the application. */
    APPROVED,
    /** Staff rejected the application. */
    REJECTED
}
