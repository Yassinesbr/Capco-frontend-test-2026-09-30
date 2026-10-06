package com.kyc.kycapplication.domain;

/** Privacy-safe KYC Application operational event types. */
public enum ApplicationAuditEventType {
    /** A new Applicant-owned draft was created. */
    APPLICATION_STARTED,
    /** An existing Applicant-owned draft was returned for resumption. */
    APPLICATION_RESUMED,
    /** An application ownership request was denied without disclosure. */
    APPLICATION_ACCESS_DENIED,
    /** A ready Applicant application was durably submitted. */
    APPLICATION_SUBMITTED
}
