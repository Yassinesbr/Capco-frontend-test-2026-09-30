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
import com.kyc.identityaccess.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterApplicantTest {

    private final CapturingRepository accountRepository = new CapturingRepository();
    private final CapturingAudit audit = new CapturingAudit();
    private final RegisterApplicant registerApplicant = new RegisterApplicant(
            accountRepository,
            new TestPasswordHashingPort(),
            audit,
            Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void registersAUniqueApplicantWithoutCreatingASession() {
        RegisteredApplicant result = registerApplicant.register(
                "Applicant@example.com", "Secret!".toCharArray());

        assertThat(result.email().value()).isEqualTo("applicant@example.com");
        assertThat(accountRepository.savedAccount.role()).isEqualTo(Role.APPLICANT);
        assertThat(accountRepository.savedAccount.passwordVerifier().encodedValue())
                .isEqualTo("hashed-value");
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(AuditEventType.APPLICANT_REGISTERED);
            assertThat(event.minimizedSubjectReference()).isEqualTo(result.id().toString());
        });
    }

    @Test
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> registerApplicant.register("not-an-email", "Secret!".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> registerApplicant.register("applicant@example.com", "weakpw".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnExistingNormalizedEmail() {
        accountRepository.emailExists = true;

        assertThatThrownBy(() -> registerApplicant.register(
                "applicant@example.com", "Secret!".toCharArray()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void aggregateDoesNotRetainRawPassword() {
        assertThat(ApplicantAccount.class.getRecordComponents())
                .extracting(component -> component.getType())
                .allMatch(type -> type != Password.class && type != char[].class);
    }

    private static final class CapturingRepository implements ApplicantAccountRepository {

        private boolean emailExists;
        private ApplicantAccount savedAccount;

        @Override
        public boolean existsByEmail(final EmailAddress email) {
            return emailExists;
        }

        @Override
        public Optional<ApplicantAccount> findByEmail(final EmailAddress email) {
            return Optional.empty();
        }

        @Override
        public void save(final ApplicantAccount account) {
            savedAccount = account;
        }
    }

    private static final class TestPasswordHashingPort implements PasswordHashingPort {

        @Override
        public PasswordVerifier hash(final Password password) {
            return new PasswordVerifier("hashed-value");
        }

        @Override
        public boolean matches(final Password password, final PasswordVerifier verifier) {
            return verifier.encodedValue().equals("hashed-value");
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
