package com.kyc.identityaccess.application;

/** Work-area authorities that are checked independently of browser navigation. */
public enum ProtectedWorkArea {
    /** Work owned by the signed-in Applicant. */
    APPLICANT,
    /** Internal work reserved for reviewers. */
    REVIEWER,
    /** Internal work reserved for administrators. */
    ADMINISTRATOR
}
