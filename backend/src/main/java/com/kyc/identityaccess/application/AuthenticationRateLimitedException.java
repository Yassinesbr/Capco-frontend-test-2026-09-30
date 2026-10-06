package com.kyc.identityaccess.application;

import java.time.Instant;

/** Signals that a credential attempt was throttled without revealing account existence. */
public final class AuthenticationRateLimitedException extends RuntimeException {

    private final Instant retryAfter;

    /** Creates a throttling outcome with the earliest time a new attempt may be made. */
    public AuthenticationRateLimitedException(final Instant retryAfter) {
        super("Too many authentication attempts");
        this.retryAfter = retryAfter;
    }

    /** Returns when the caller may retry authentication. */
    public Instant retryAfter() {
        return retryAfter;
    }
}
