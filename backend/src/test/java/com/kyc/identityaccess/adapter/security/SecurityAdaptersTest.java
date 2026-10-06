package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeRateLimitKey;
import com.kyc.identityaccess.domain.Password;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityAdaptersTest {

    @Test
    void hashesAndMatchesPasswordWithoutKeepingPlaintextVerifier() {
        SpringArgon2PasswordHashingAdapter adapter = new SpringArgon2PasswordHashingAdapter();
        PasswordVerifier verifier;
        try (Password password = new Password("Secret!".toCharArray())) {
            verifier = adapter.hash(password);
        }
        try (Password password = new Password("Secret!".toCharArray())) {
            assertThat(adapter.matches(password, verifier)).isTrue();
        }
        assertThat(verifier.encodedValue()).doesNotContain("Secret!");
    }

    @Test
    void createsDistinctOpaqueCsrpngSessionIdentifiers() {
        SecureRandomSessionIdGenerator generator = new SecureRandomSessionIdGenerator();

        SessionId first = generator.generate();
        SessionId second = generator.generate();

        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.value()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void issuesHostScopedHttpOnlySecureCookie() {
        SessionCookieFactory factory = new SessionCookieFactory(new SessionSecurityProperties(
                Duration.ofMinutes(30), Duration.ofHours(8), "Strict", true));

        String header = factory.issue(new SessionId("opaque-session")).toString();

        assertThat(header).contains("__Host-KYCSESSION=opaque-session");
        assertThat(header).contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict");
        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void clearsTheOpaqueCookieOnSessionRevocation() {
        SessionCookieFactory factory = new SessionCookieFactory(new SessionSecurityProperties(
                Duration.ofMinutes(30), Duration.ofHours(8), "Strict", true));

        String header = factory.clear().toString();

        assertThat(header).contains("__Host-KYCSESSION=", "Max-Age=0", "Path=/", "Secure", "HttpOnly");
        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void rejectsUnsafeHostCookieConfiguration() {
        assertThatThrownBy(() -> new SessionSecurityProperties(
                Duration.ofMinutes(30), Duration.ofHours(8), "None", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAttemptsAfterConfiguredFailureLimit() {
        AuthenticationRateLimitPort limiter = new InMemoryAuthenticationRateLimiter(
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                2,
                Duration.ofMinutes(15));
        PrivacySafeRateLimitKey key = new PrivacySafeRateLimitKey("hashed-signal");

        limiter.recordFailure(key);
        limiter.recordFailure(key);

        assertThat(limiter.check(key).allowed()).isFalse();
    }

}
