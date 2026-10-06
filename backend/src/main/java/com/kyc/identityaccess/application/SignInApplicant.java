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
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authenticates an Applicant and issues a rotated, server-side session. */
public final class SignInApplicant {

    private final ApplicantAccountRepository accountRepository;
    private final ApplicantSessionRepository sessionRepository;
    private final PasswordHashingPort passwordHashingPort;
    private final SessionIdGenerator sessionIdGenerator;
    private final CurrentApplicantJourney currentApplicantJourney;
    private final PrivacySafeAuditEventPublisher auditEventPublisher;
    private final AuthenticationRateLimitPort rateLimitPort;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;

    /** Creates the sign-in use case from explicit application boundaries and session policy. */
    public SignInApplicant(
            final ApplicantAccountRepository accountRepository,
            final ApplicantSessionRepository sessionRepository,
            final PasswordHashingPort passwordHashingPort,
            final SessionIdGenerator sessionIdGenerator,
            final CurrentApplicantJourney currentApplicantJourney,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final AuthenticationRateLimitPort rateLimitPort,
            final Clock clock,
            final Duration idleTimeout,
            final Duration absoluteTimeout) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.passwordHashingPort = Objects.requireNonNull(passwordHashingPort, "passwordHashingPort must not be null");
        this.sessionIdGenerator = Objects.requireNonNull(sessionIdGenerator, "sessionIdGenerator must not be null");
        this.currentApplicantJourney = Objects.requireNonNull(
                currentApplicantJourney, "currentApplicantJourney must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.rateLimitPort = Objects.requireNonNull(rateLimitPort, "rateLimitPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idleTimeout = requirePositive(idleTimeout, "idleTimeout");
        this.absoluteTimeout = requirePositive(absoluteTimeout, "absoluteTimeout");
        if (absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException("absoluteTimeout must be at least idleTimeout");
        }
    }

    /** Authenticates without a prior session and returns a newly issued session plus journey. */
    public SignedInApplicant signIn(
            final String rawEmail, final char[] rawPassword, final PrivacySafeRateLimitKey rateLimitKey) {
        return signIn(rawEmail, rawPassword, rateLimitKey, Optional.empty());
    }

    /**
     * Authenticates, revoking a supplied active session only after credentials are verified and a
     * new session is ready. This prevents session fixation during an already-authenticated sign-in.
     */
    public SignedInApplicant signIn(
            final String rawEmail,
            final char[] rawPassword,
            final PrivacySafeRateLimitKey rateLimitKey,
            final Optional<SessionId> sessionToRotate) {
        Objects.requireNonNull(rawEmail, "rawEmail must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(rateLimitKey, "rateLimitKey must not be null");
        Objects.requireNonNull(sessionToRotate, "sessionToRotate must not be null");

        AuthenticationRateLimitPort.RateLimitDecision decision = rateLimitPort.check(rateLimitKey);
        if (!decision.allowed()) {
            publishFailure("throttled", clock.instant());
            throw new AuthenticationRateLimitedException(decision.retryAfter());
        }

        Instant now = clock.instant();
        EmailAddress email;
        try {
            email = new EmailAddress(rawEmail);
        } catch (IllegalArgumentException exception) {
            rateLimitPort.recordFailure(rateLimitKey);
            publishFailure("failure", now);
            throw new AuthenticationFailedException();
        }

        Optional<ApplicantAccount> account = accountRepository.findByEmail(email);
        boolean credentialMatches = false;
        try (Password password = new Password(rawPassword)) {
            credentialMatches = account
                    .map(candidate -> passwordHashingPort.matches(password, candidate.passwordVerifier()))
                    .orElse(false);
        } catch (IllegalArgumentException exception) {
            credentialMatches = false;
        }

        if (!credentialMatches) {
            rateLimitPort.recordFailure(rateLimitKey);
            publishFailure("failure", now);
            throw new AuthenticationFailedException();
        }

        ApplicantAccount authenticatedAccount = account.orElseThrow(AuthenticationFailedException::new);
        ApplicantJourney journey = currentApplicantJourney.findFor(authenticatedAccount.id());
        SessionId newSessionId = sessionIdGenerator.generate();
        ApplicantSession newSession = new ApplicantSession(
                newSessionId,
                authenticatedAccount.id(),
                authenticatedAccount.role(),
                now,
                now.plus(idleTimeout),
                now.plus(absoluteTimeout),
                null);
        sessionRepository.save(newSession);
        sessionToRotate.ifPresent(previousSessionId -> sessionRepository.revoke(previousSessionId, now));
        rateLimitPort.clear(rateLimitKey);
        auditEventPublisher.publish(new PrivacySafeAuditEvent(
                AuditEventType.APPLICANT_SIGNED_IN,
                "success",
                now,
                UUID.randomUUID(),
                authenticatedAccount.id().toString()));
        return new SignedInApplicant(newSessionId, journey);
    }

    private void publishFailure(final String outcome, final Instant now) {
        auditEventPublisher.publish(new PrivacySafeAuditEvent(
                AuditEventType.AUTHENTICATION_FAILED,
                outcome,
                now,
                UUID.randomUUID(),
                "credential-attempt"));
    }

    private static Duration requirePositive(final Duration duration, final String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
