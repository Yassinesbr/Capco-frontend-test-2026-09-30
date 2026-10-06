package com.kyc.identityaccess.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated deployment configuration for opaque Applicant sessions. */
@ConfigurationProperties(prefix = "kyc.security.session")
public record SessionSecurityProperties(
        Duration idleTimeout,
        Duration absoluteTimeout,
        String sameSite,
        boolean secureCookies) {

    /** Validates required production-safe session settings. */
    public SessionSecurityProperties {
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        if (absoluteTimeout == null || absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException("absoluteTimeout must be at least idleTimeout");
        }
        if (!"Strict".equals(sameSite) && !"Lax".equals(sameSite)) {
            throw new IllegalArgumentException("sameSite must be Strict or Lax");
        }
        if (!secureCookies) {
            throw new IllegalArgumentException("secureCookies must be enabled for __Host- cookies");
        }
    }
}
