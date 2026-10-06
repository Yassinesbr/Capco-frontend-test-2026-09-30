package com.kyc.kycapplication.application.port.out;

import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;

/** Atomic persistence boundary for a submitted lifecycle transition and its audit fact. */
public interface ApplicationSubmissionRepository {

    /** Commits only when the draft and ready form still match the confirmed version. */
    boolean commitSubmission(
            KycApplication submittedApplication,
            int expectedFormVersion,
            PrivacySafeApplicationAuditEvent auditEvent);
}
