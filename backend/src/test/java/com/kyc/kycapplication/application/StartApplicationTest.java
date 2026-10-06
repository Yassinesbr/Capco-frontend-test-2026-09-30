package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.ActiveDraftConflictException;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.application.port.out.PrivacySafeApplicationAuditPublisher;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationAuditEventType;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void repeatedStartReturnsTheSingleExistingDraft() {
        InMemoryApplications repository = new InMemoryApplications();
        CapturingAudit audit = new CapturingAudit();
        StartApplication start = new StartApplication(repository, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        ApplicantId owner = applicant();

        StartApplication.StartResult first = start.start(owner);
        StartApplication.StartResult repeated = start.start(owner);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.application().id()).isEqualTo(first.application().id());
        assertThat(repository.applications).hasSize(1);
        assertThat(audit.events).extracting(PrivacySafeApplicationAuditEvent::type)
                .containsExactly(ApplicationAuditEventType.APPLICATION_STARTED, ApplicationAuditEventType.APPLICATION_RESUMED);
    }

    @Test
    void conflictRecoveryReturnsTheDraftCreatedByAConcurrentStart() {
        ApplicantId owner = applicant();
        KycApplication concurrentDraft = KycApplication.start(owner, NOW);
        KycApplicationRepository repository = new KycApplicationRepository() {
            private boolean firstLookup = true;

            @Override
            public Optional<KycApplication> findActiveByOwner(final ApplicantId ignored) {
                if (firstLookup) {
                    firstLookup = false;
                    return Optional.empty();
                }
                return Optional.of(concurrentDraft);
            }

            @Override
            public Optional<KycApplication> findById(final ApplicationId ignored) {
                return Optional.empty();
            }

            @Override
            public void save(final KycApplication ignored) {
                throw new ActiveDraftConflictException(new IllegalStateException("unique index"));
            }
        };

        StartApplication.StartResult result = new StartApplication(
                        repository, new CapturingAudit(), Clock.fixed(NOW, ZoneOffset.UTC))
                .start(owner);

        assertThat(result.created()).isFalse();
        assertThat(result.application()).isEqualTo(concurrentDraft);
    }

    private static ApplicantId applicant() {
        return new ApplicantId(UUID.fromString("0a6c7d5e-6d4e-4b67-a7f8-3f67a6249b5e"));
    }

    private static final class InMemoryApplications implements KycApplicationRepository {

        private final Map<ApplicantId, KycApplication> applications = new HashMap<>();

        @Override
        public Optional<KycApplication> findActiveByOwner(final ApplicantId ownerId) {
            return Optional.ofNullable(applications.get(ownerId));
        }

        @Override
        public Optional<KycApplication> findById(final ApplicationId applicationId) {
            return applications.values().stream().filter(application -> application.id().equals(applicationId)).findFirst();
        }

        @Override
        public void save(final KycApplication application) {
            applications.putIfAbsent(application.ownerId(), application);
        }
    }

    private static final class CapturingAudit implements PrivacySafeApplicationAuditPublisher {

        private final java.util.List<PrivacySafeApplicationAuditEvent> events = new java.util.ArrayList<>();

        @Override
        public void publish(final PrivacySafeApplicationAuditEvent event) {
            events.add(event);
        }
    }
}
