package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.Role;
import com.kyc.identityaccess.domain.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicantAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void anApplicantCanAccessOnlyApplicantWork() {
        Fixture fixture = activeApplicantFixture();

        AuthorizedApplicant authorized = fixture.authorize.authorize(
                fixture.sessionId, ProtectedWorkArea.APPLICANT);

        assertThat(authorized.accountId()).isEqualTo(fixture.account.id());
        assertThat(fixture.audit.events).isEmpty();
    }

    @Test
    void anApplicantCannotSatisfyReviewerOrAdministratorAuthorization() {
        Fixture fixture = activeApplicantFixture();

        assertThatThrownBy(() -> fixture.authorize.authorize(fixture.sessionId, ProtectedWorkArea.REVIEWER))
                .isInstanceOf(ApplicantAccessDeniedException.class)
                .hasMessage("Access denied.");
        assertThatThrownBy(() -> fixture.authorize.authorize(fixture.sessionId, ProtectedWorkArea.ADMINISTRATOR))
                .isInstanceOf(ApplicantAccessDeniedException.class)
                .hasMessage("Access denied.");

        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::type)
                .containsExactly(AuditEventType.AUTHORIZATION_DENIED, AuditEventType.AUTHORIZATION_DENIED);
        assertThat(fixture.audit.events).extracting(PrivacySafeAuditEvent::minimizedSubjectReference)
                .containsOnly(fixture.account.id().toString());
    }

    @Test
    void missingOrExpiredSessionsAreDeniedWithoutDisclosingTheRawSessionId() {
        Fixture fixture = activeApplicantFixture();
        SessionId unknownSession = new SessionId("raw-session-id-must-not-be-audited");

        assertThatThrownBy(() -> fixture.authorize.authorize(unknownSession, ProtectedWorkArea.APPLICANT))
                .isInstanceOf(ApplicantAccessDeniedException.class)
                .hasMessage("Access denied.");

        assertThat(fixture.audit.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(AuditEventType.AUTHORIZATION_DENIED);
            assertThat(event.minimizedSubjectReference()).isEqualTo("anonymous-request");
            assertThat(event.toString()).doesNotContain(unknownSession.value());
        });
    }

    @Test
    void auditFactsRejectCredentialsVerifiersAndRawSessionIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PrivacySafeAuditEvent(
                AuditEventType.AUTHENTICATION_FAILED,
                "failure",
                NOW,
                UUID.randomUUID(),
                "MySecret!"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PrivacySafeAuditEvent(
                AuditEventType.APPLICANT_REGISTERED,
                "success",
                NOW,
                UUID.randomUUID(),
                "$argon2id$v=19$m=19456$verifier"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PrivacySafeAuditEvent(
                AuditEventType.AUTHORIZATION_DENIED,
                "denied",
                NOW,
                UUID.randomUUID(),
                "raw-opaque-session-id"));
    }

    private static Fixture activeApplicantFixture() {
        ApplicantAccount account = ApplicantAccount.register(
                new EmailAddress("applicant@example.com"),
                new PasswordVerifier("$argon2id$v=19$m=19456$verifier"),
                NOW);
        SessionId sessionId = new SessionId("server-side-opaque-session");
        InMemorySessions sessions = new InMemorySessions();
        sessions.save(new ApplicantSession(
                sessionId,
                account.id(),
                Role.APPLICANT,
                NOW,
                NOW.plus(Duration.ofMinutes(30)),
                NOW.plus(Duration.ofHours(8)),
                null));
        CapturingAudit audit = new CapturingAudit();
        AuthorizeApplicantAccess authorize = new AuthorizeApplicantAccess(
                sessions,
                audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(account, sessionId, audit, authorize);
    }

    private record Fixture(
            ApplicantAccount account,
            SessionId sessionId,
            CapturingAudit audit,
            AuthorizeApplicantAccess authorize) {
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

    private static final class CapturingAudit implements PrivacySafeAuditEventPublisher {

        private final List<PrivacySafeAuditEvent> events = new ArrayList<>();

        @Override
        public void publish(final PrivacySafeAuditEvent event) {
            events.add(event);
        }
    }
}
