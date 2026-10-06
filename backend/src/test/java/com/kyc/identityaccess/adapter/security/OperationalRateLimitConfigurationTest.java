package com.kyc.identityaccess.adapter.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies unsafe rate-limit deployments fail before they can serve authentication requests. */
class OperationalRateLimitConfigurationTest {

    @Test
    void rejectsInvalidOperationalRateLimitConfigurationBeforeServingRequests() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> new InMemoryAuthenticationRateLimiter(clock, 0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate-limit configuration is invalid");
        assertThatThrownBy(() -> new InMemoryAuthenticationRateLimiter(clock, 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate-limit configuration is invalid");
    }
}
