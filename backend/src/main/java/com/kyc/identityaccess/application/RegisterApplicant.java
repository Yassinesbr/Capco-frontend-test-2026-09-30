package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.ApplicantAccountRepository;
import com.kyc.identityaccess.application.port.out.PasswordHashingPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.Password;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registers an Applicant account without issuing an authenticated session. */
public final class RegisterApplicant {

    private final ApplicantAccountRepository accountRepository;
    private final PasswordHashingPort passwordHashingPort;
    private final PrivacySafeAuditEventPublisher auditEventPublisher;
    private final Clock clock;

    /** Creates the registration use case with explicit infrastructure ports. */
    public RegisterApplicant(
            final ApplicantAccountRepository accountRepository,
            final PasswordHashingPort passwordHashingPort,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.passwordHashingPort = Objects.requireNonNull(passwordHashingPort);
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Registers a unique applicant and returns its public registration result. */
    public RegisteredApplicant register(final String emailValue, final char[] passwordCharacters) {
        EmailAddress email = new EmailAddress(emailValue);
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        try (Password password = new Password(passwordCharacters)) {
            PasswordVerifier verifier = passwordHashingPort.hash(password);
            Instant now = clock.instant();
            ApplicantAccount account = ApplicantAccount.register(email, verifier, now);
            accountRepository.save(account);
            auditEventPublisher.publish(new PrivacySafeAuditEvent(
                    AuditEventType.APPLICANT_REGISTERED,
                    "success",
                    now,
                    UUID.randomUUID(),
                    account.id().toString()));
            return new RegisteredApplicant(account.id(), account.email());
        }
    }
}
