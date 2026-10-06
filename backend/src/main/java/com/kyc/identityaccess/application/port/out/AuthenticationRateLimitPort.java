package com.kyc.identityaccess.application.port.out;

import java.time.Instant;

/** Abuse-control boundary for registration and authentication attempts. */
public interface AuthenticationRateLimitPort {

    /** Evaluates whether an attempt with a privacy-safe key may proceed. */
    RateLimitDecision check(PrivacySafeRateLimitKey key);

    /** Records a failed attempt after credential verification. */
    void recordFailure(PrivacySafeRateLimitKey key);

    /** Clears failure state after successful authentication. */
    void clear(PrivacySafeRateLimitKey key);

    /** Decision returned by the rate-limit boundary. */
    record RateLimitDecision(boolean allowed, Instant retryAfter) {
    }
}
