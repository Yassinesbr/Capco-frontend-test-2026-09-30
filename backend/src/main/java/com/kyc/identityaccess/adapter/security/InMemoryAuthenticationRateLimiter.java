package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeRateLimitKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fixed-window rate-limit adapter for a privacy-safe key. */
public final class InMemoryAuthenticationRateLimiter implements AuthenticationRateLimitPort {

    private final ConcurrentHashMap<PrivacySafeRateLimitKey, AttemptWindow> attempts =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximumFailures;
    private final Duration window;

    /** Creates an adapter with validated operational limits. */
    public InMemoryAuthenticationRateLimiter(
            final Clock clock, final int maximumFailures, final Duration window) {
        if (maximumFailures < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("rate-limit configuration is invalid");
        }
        this.clock = clock;
        this.maximumFailures = maximumFailures;
        this.window = window;
    }

    /** {@inheritDoc} */
    @Override
    public RateLimitDecision check(final PrivacySafeRateLimitKey key) {
        Instant now = clock.instant();
        AttemptWindow attemptWindow = attempts.get(key);
        if (attemptWindow == null) {
            return new RateLimitDecision(true, null);
        }
        if (!attemptWindow.expiresAt().isAfter(now)) {
            attempts.remove(key, attemptWindow);
            return new RateLimitDecision(true, null);
        }
        return new RateLimitDecision(attemptWindow.failures() < maximumFailures, attemptWindow.expiresAt());
    }

    /** {@inheritDoc} */
    @Override
    public void recordFailure(final PrivacySafeRateLimitKey key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, attemptWindow) -> {
            if (attemptWindow == null || !attemptWindow.expiresAt().isAfter(now)) {
                return new AttemptWindow(1, now.plus(window));
            }
            return new AttemptWindow(attemptWindow.failures() + 1, attemptWindow.expiresAt());
        });
    }

    /** {@inheritDoc} */
    @Override
    public void clear(final PrivacySafeRateLimitKey key) {
        attempts.remove(key);
    }

    private record AttemptWindow(int failures, Instant expiresAt) {
    }
}
