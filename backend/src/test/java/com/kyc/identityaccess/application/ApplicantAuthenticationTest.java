package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.ApplicantAccountRepository;
import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.CurrentApplicantJourney;
import com.kyc.identityaccess.application.port.out.PasswordHashingPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.application.port.out.PrivacySafeRateLimitKey;
import com.kyc.identityaccess.application.port.out.SessionIdGenerator;
import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.Password;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicantAuthenticationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-09-22T12:00:00Z");
    private static final PrivacySafeRateLimitKey RATE_LIMIT_KEY =
            new PrivacySafeRateLimitKey("hashed-client-signal");

    @Test
    void validCredentialsIssueARotatedSessionAndUseTheCurrentApplicantJourney() {
        Fixture fixture = fixtureWithRegisteredApplicant();
        SessionId priorSessionId = new SessionId("prior-session");
        fixture.sessions.save(session(priorSessionId, fixture.account, fixture.clock.instant()));

        SignedInApplicant result = fixture.signIn.signIn(
                fixture.account.email().value(), "Secret!".toCharArray(), RATE_LIMIT_KEY, Optional.of(priorSessionId));

        assertThat(result.sessionId()).isNotEqualTo(priorSessionId);
        assertThat(result.journey().href()).isEqualTo("/application/current");
        assertThat(fixture.sessions.findActiveById(priorSessionId, fixture.clock.instant())).isEmpty();
        assertThat(fixture.sessions.findActiveById(result.sessionId(), fixture.clock.instant())).isPresent();
        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::type)
                .containsExactly(AuditEventType.APPLICANT_SIGNED_IN);
        assertThat(fixture.rateLimiter.clearCalls).isEqualTo(1);
    }

    @Test
    void unknownEmailAndWrongPasswordExposeTheSamePublicFailure() {
        Fixture fixture = fixtureWithRegisteredApplicant();

        Throwable unknownEmail = captureFailure(() -> fixture.signIn.signIn(
                "unknown@example.com", "Secret!".toCharArray(), RATE_LIMIT_KEY));
        Throwable wrongPassword = captureFailure(() -> fixture.signIn.signIn(
                fixture.account.email().value(), "Another!".toCharArray(), RATE_LIMIT_KEY));

        assertThat(unknownEmail).isInstanceOf(AuthenticationFailedException.class);
        assertThat(wrongPassword).isInstanceOf(AuthenticationFailedException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::type)
                .containsOnly(AuditEventType.AUTHENTICATION_FAILED);
        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::minimizedSubjectReference)
                .containsOnly("credential-attempt");
    }

    @Test
    void repeatedCredentialFailuresAreThrottledWithoutAttemptingAnotherLookup() {
        Fixture fixture = fixtureWithRegisteredApplicant();

        captureFailure(() -> fixture.signIn.signIn(
                "unknown@example.com", "Secret!".toCharArray(), RATE_LIMIT_KEY));
        captureFailure(() -> fixture.signIn.signIn(
                "unknown@example.com", "Secret!".toCharArray(), RATE_LIMIT_KEY));

        assertThatThrownBy(() -> fixture.signIn.signIn(
                "unknown@example.com", "Secret!".toCharArray(), RATE_LIMIT_KEY))
                .isInstanceOf(AuthenticationRateLimitedException.class);
        assertThat(fixture.accounts.findByEmailCalls).isEqualTo(2);
        assertThat(fixture.audit.events.getLast().outcome()).isEqualTo("throttled");
    }

    @Test
    void expiredSessionCannotBeSignedOutOrUsedAfterItsIdleLifetime() {
        Fixture fixture = fixtureWithRegisteredApplicant();
        SignedInApplicant result = fixture.signIn.signIn(
                fixture.account.email().value(), "Secret!".toCharArray(), RATE_LIMIT_KEY);
        fixture.clock.advance(Duration.ofMinutes(31));

        assertThat(fixture.sessions.findActiveById(result.sessionId(), fixture.clock.instant())).isEmpty();
        assertThatThrownBy(() -> fixture.signOut.signOut(result.sessionId()))
                .isInstanceOf(NoActiveApplicantSessionException.class);
    }

    @Test
    void signOutRevokesTheSessionAndDeniesItsLaterUse() {
        Fixture fixture = fixtureWithRegisteredApplicant();
        SignedInApplicant result = fixture.signIn.signIn(
                fixture.account.email().value(), "Secret!".toCharArray(), RATE_LIMIT_KEY);

        fixture.signOut.signOut(result.sessionId());

        assertThat(fixture.sessions.findActiveById(result.sessionId(), fixture.clock.instant())).isEmpty();
        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::type)
                .contains(AuditEventType.APPLICANT_SIGNED_OUT);
    }

    private static Fixture fixtureWithRegisteredApplicant() {
        MutableClock clock = new MutableClock(INITIAL_TIME);
        ApplicantAccount account = ApplicantAccount.register(
                new EmailAddress("applicant@example.com"), new PasswordVerifier("expected-secret"), INITIAL_TIME);
        InMemoryAccounts accounts = new InMemoryAccounts(account);
        InMemorySessions sessions = new InMemorySessions();
        CapturingAudit audit = new CapturingAudit();
        TestRateLimiter rateLimiter = new TestRateLimiter(clock);
        SignInApplicant signIn = new SignInApplicant(
                accounts,
                sessions,
                new ExpectedPasswordHashingPort(),
                new IncrementingSessionIds(),
                applicantId -> {
                    assertThat(applicantId).isEqualTo(account.id());
                    return new ApplicantJourney("/application/current");
                },
                audit,
                rateLimiter,
                clock,
                Duration.ofMinutes(30),
                Duration.ofHours(8));
        return new Fixture(account, accounts, sessions, audit, rateLimiter, clock, signIn,
                new SignOutApplicant(sessions, audit, clock));
    }

    private static ApplicantSession session(
            final SessionId id, final ApplicantAccount account, final Instant createdAt) {
        return new ApplicantSession(
                id,
                account.id(),
                account.role(),
                createdAt,
                createdAt.plus(Duration.ofMinutes(30)),
                createdAt.plus(Duration.ofHours(8)),
                null);
    }

    private static Throwable captureFailure(final ThrowingOperation operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError("Expected authentication to fail");
    }

    private record Fixture(
            ApplicantAccount account,
            InMemoryAccounts accounts,
            InMemorySessions sessions,
            CapturingAudit audit,
            TestRateLimiter rateLimiter,
            MutableClock clock,
            SignInApplicant signIn,
            SignOutApplicant signOut) {
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private static final class InMemoryAccounts implements ApplicantAccountRepository {

        private final Map<EmailAddress, ApplicantAccount> accounts = new HashMap<>();
        private int findByEmailCalls;

        InMemoryAccounts(final ApplicantAccount account) {
            accounts.put(account.email(), account);
        }

        @Override
        public boolean existsByEmail(final EmailAddress email) {
            return accounts.containsKey(email);
        }

        @Override
        public Optional<ApplicantAccount> findByEmail(final EmailAddress email) {
            findByEmailCalls++;
            return Optional.ofNullable(accounts.get(email));
        }

        @Override
        public void save(final ApplicantAccount account) {
            accounts.put(account.email(), account);
        }
    }

    private static final class InMemorySessions implements ApplicantSessionRepository {

        private final Map<SessionId, ApplicantSession> sessions = new HashMap<>();

        @Override
        public void save(final ApplicantSession session) {
            sessions.put(session.id(), session);
        }

        @Override
        public Optional<ApplicantSession> findActiveById(final SessionId sessionId, final Instant instant) {
            return Optional.ofNullable(sessions.get(sessionId)).filter(session -> session.isActiveAt(instant));
        }

        @Override
        public void revoke(final SessionId sessionId, final Instant revokedAt) {
            sessions.computeIfPresent(sessionId, (ignored, session) -> session.revoke(revokedAt));
        }
    }

    private static final class ExpectedPasswordHashingPort implements PasswordHashingPort {

        @Override
        public PasswordVerifier hash(final Password password) {
            return new PasswordVerifier("expected-secret");
        }

        @Override
        public boolean matches(final Password password, final PasswordVerifier verifier) {
            return verifier.encodedValue().equals("expected-secret")
                    && String.valueOf(password.copyCharacters()).equals("Secret!");
        }
    }

    private static final class IncrementingSessionIds implements SessionIdGenerator {

        private int nextId;

        @Override
        public SessionId generate() {
            nextId++;
            return new SessionId("rotated-session-" + nextId);
        }
    }

    private static final class CapturingAudit implements PrivacySafeAuditEventPublisher {

        private final List<PrivacySafeAuditEvent> events = new ArrayList<>();

        @Override
        public void publish(final PrivacySafeAuditEvent event) {
            events.add(event);
        }
    }

    private static final class TestRateLimiter implements AuthenticationRateLimitPort {

        private final MutableClock clock;
        private int failures;
        private int clearCalls;

        TestRateLimiter(final MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public RateLimitDecision check(final PrivacySafeRateLimitKey key) {
            return new RateLimitDecision(failures < 2, clock.instant().plus(Duration.ofMinutes(15)));
        }

        @Override
        public void recordFailure(final PrivacySafeRateLimitKey key) {
            failures++;
        }

        @Override
        public void clear(final PrivacySafeRateLimitKey key) {
            clearCalls++;
            failures = 0;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
