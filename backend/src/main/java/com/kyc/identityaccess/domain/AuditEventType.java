package com.kyc.identityaccess.domain;

/** Privacy-safe security event categories emitted by account access. */
public enum AuditEventType {
    /** An Applicant account was registered. */
    APPLICANT_REGISTERED,
    /** An Applicant signed in. */
    APPLICANT_SIGNED_IN,
    /** An Applicant signed out. */
    APPLICANT_SIGNED_OUT,
    /** A credential attempt was rejected. */
    AUTHENTICATION_FAILED,
    /** An authorization request was denied. */
    AUTHORIZATION_DENIED
}
