package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;

/** Boundary for recording privacy-minimized Identity and Access facts. */
public interface PrivacySafeAuditEventPublisher {

    /** Stores one privacy-safe audit event without credential or session material. */
    void publish(PrivacySafeAuditEvent event);
}
