package com.kyc.kycapplication.application.port.out;

import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;

/** Outbound boundary for KYC Application operational observations. */
public interface PrivacySafeApplicationAuditPublisher {

    /** Records only a previously validated privacy-safe observation. */
    void publish(PrivacySafeApplicationAuditEvent event);
}
