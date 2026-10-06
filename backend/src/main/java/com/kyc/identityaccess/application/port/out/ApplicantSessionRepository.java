package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for revocable Applicant sessions. */
public interface ApplicantSessionRepository {

    /** Persists a newly issued or rotated Applicant session. */
    void save(ApplicantSession session);

    /** Returns a session only while it remains active at the supplied instant. */
    Optional<ApplicantSession> findActiveById(SessionId sessionId, Instant instant);

    /** Revokes a session so later authorization cannot use it. */
    void revoke(SessionId sessionId, Instant revokedAt);
}
