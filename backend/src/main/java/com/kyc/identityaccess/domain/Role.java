package com.kyc.identityaccess.domain;

/** Roles recognized by the Identity and Access bounded context. */
public enum Role {
    /** Public KYC applicant role. */
    APPLICANT,
    /** Internal review role. */
    REVIEWER,
    /** Internal administration role. */
    ADMINISTRATOR
}
