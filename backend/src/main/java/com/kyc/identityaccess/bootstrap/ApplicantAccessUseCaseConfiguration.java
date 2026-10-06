package com.kyc.identityaccess.bootstrap;

import com.kyc.identityaccess.adapter.security.SessionSecurityProperties;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.RegisterApplicant;
import com.kyc.identityaccess.application.SignInApplicant;
import com.kyc.identityaccess.application.SignOutApplicant;
import com.kyc.identityaccess.application.port.out.ApplicantAccountRepository;
import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.CurrentApplicantJourney;
import com.kyc.identityaccess.application.port.out.PasswordHashingPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.application.port.out.SessionIdGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires framework-independent Applicant account use cases to their production adapters. */
@Configuration(proxyBeanMethods = false)
public class ApplicantAccessUseCaseConfiguration {

    /** Wires registration to SQLite and the password adapter. */
    @Bean
    RegisterApplicant registerApplicant(
            final ApplicantAccountRepository accountRepository,
            final PasswordHashingPort passwordHashingPort,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        return new RegisterApplicant(accountRepository, passwordHashingPort, auditEventPublisher, clock);
    }

    /** Wires sign-in to its explicit session, journey, audit, and rate-limit boundaries. */
    @Bean
    SignInApplicant signInApplicant(
            final ApplicantAccountRepository accountRepository,
            final ApplicantSessionRepository sessionRepository,
            final PasswordHashingPort passwordHashingPort,
            final SessionIdGenerator sessionIdGenerator,
            final CurrentApplicantJourney currentApplicantJourney,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final AuthenticationRateLimitPort rateLimitPort,
            final Clock clock,
            final SessionSecurityProperties sessionSecurityProperties) {
        return new SignInApplicant(
                accountRepository,
                sessionRepository,
                passwordHashingPort,
                sessionIdGenerator,
                currentApplicantJourney,
                auditEventPublisher,
                rateLimitPort,
                clock,
                sessionSecurityProperties.idleTimeout(),
                sessionSecurityProperties.absoluteTimeout());
    }

    /** Wires sign-out to revocable sessions and privacy-safe audit observations. */
    @Bean
    SignOutApplicant signOutApplicant(
            final ApplicantSessionRepository sessionRepository,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        return new SignOutApplicant(sessionRepository, auditEventPublisher, clock);
    }

    /** Wires server-enforced work-area checks to active Applicant sessions. */
    @Bean
    AuthorizeApplicantAccess authorizeApplicantAccess(
            final ApplicantSessionRepository sessionRepository,
            final PrivacySafeAuditEventPublisher auditEventPublisher,
            final Clock clock) {
        return new AuthorizeApplicantAccess(sessionRepository, auditEventPublisher, clock);
    }
}
