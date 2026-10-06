package com.kyc.kycapplication.adapter.persistence;

import com.kyc.identityaccess.adapter.persistence.SQLiteApplicantAccountRepository;
import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.kycapplication.application.port.out.ActiveDraftConflictException;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStep;
import com.kyc.kycapplication.domain.ApplicationAuditEventType;
import com.kyc.kycapplication.domain.ApplicationStatus;
import com.kyc.kycapplication.domain.DocumentStorageReference;
import com.kyc.kycapplication.domain.FormField;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.EnumMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies KYC Application persistence against a clean, migrated SQLite boundary. */
class SQLiteKycApplicationPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @TempDir
    private Path databaseDirectory;

    private JdbcTemplate jdbcTemplate;
    private SQLiteApplicantAccountRepository accountRepository;
    private SQLiteKycApplicationRepository applicationRepository;
    private SQLiteKycApplicationFormRepository formRepository;
    private SQLitePrivacySafeApplicationAuditPublisher auditPublisher;

    @BeforeEach
    void migrateCleanSqliteDatabase() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve("kyc-application.db").toAbsolutePath());
        dataSource.setEnforceForeignKeys(true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        Flyway.configure()
                .cleanDisabled(false)
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        accountRepository = new SQLiteApplicantAccountRepository(jdbcTemplate);
        applicationRepository = new SQLiteKycApplicationRepository(jdbcTemplate);
        formRepository = new SQLiteKycApplicationFormRepository(jdbcTemplate);
        auditPublisher = new SQLitePrivacySafeApplicationAuditPublisher(jdbcTemplate);
    }

    @Test
    void permitsOneActiveDraftPerOwnerAndIndependentDraftsForOtherOwners() {
        ApplicantId firstOwner = account("first@example.com");
        ApplicantId secondOwner = account("second@example.com");
        KycApplication firstDraft = KycApplication.start(firstOwner, NOW);

        applicationRepository.save(firstDraft);

        assertThat(applicationRepository.findActiveByOwner(firstOwner)).contains(firstDraft);
        assertThatThrownBy(() -> applicationRepository.save(KycApplication.start(firstOwner, NOW)))
                .isInstanceOf(ActiveDraftConflictException.class);

        KycApplication secondDraft = KycApplication.start(secondOwner, NOW);
        applicationRepository.save(secondDraft);
        assertThat(applicationRepository.findActiveByOwner(secondOwner)).contains(secondDraft);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kyc_applications", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsAnApplicationWhoseOwnerDoesNotExist() {
        assertThatThrownBy(() -> applicationRepository.save(KycApplication.start(
                        new ApplicantId(UUID.randomUUID()), NOW)))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ActiveDraftConflictException.class);
    }

    @Test
    void readsApprovedAtFromPersistedApprovedApplication() {
        ApplicantId owner = account("approved@example.com");
        Instant submittedAt = NOW.minusSeconds(3600);
        Instant approvedAt = NOW.minusSeconds(60);
        ApplicationId applicationId = new ApplicationId(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO kyc_applications (
                    id, applicant_account_id, lifecycle, current_step, created_at, updated_at, submitted_at, approved_at
                ) VALUES (?, ?, 'APPROVED', 'PERSONAL_DETAILS', ?, ?, ?, ?)
                """, applicationId.value().toString(), owner.value().toString(), NOW.minusSeconds(7200).toString(),
                NOW.toString(), submittedAt.toString(), approvedAt.toString());

        assertThat(applicationRepository.findById(applicationId)).get()
                .satisfies(application -> {
                    assertThat(application.status()).isEqualTo(ApplicationStatus.APPROVED);
                    assertThat(application.approvedAt()).isEqualTo(approvedAt);
                });
    }

    @Test
    void persistsOnlyPrivacySafeKycApplicationAuditFields() {
        auditPublisher.publish(new PrivacySafeApplicationAuditEvent(
                ApplicationAuditEventType.APPLICATION_STARTED,
                "success",
                NOW,
                UUID.randomUUID(),
                UUID.randomUUID().toString()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_STARTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM pragma_table_info('kyc_application_audit_events')", String.class))
                .noneMatch(column -> column.contains("answer")
                        || column.contains("address")
                        || column.contains("document")
                        || column.contains("route"));
    }

    @Test
    void upgradesPopulatedV3DataWithoutLosingFormsAuditRowsOrIndexes() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve("populated-v3.db").toAbsolutePath());
        dataSource.setEnforceForeignKeys(true);
        JdbcTemplate populatedDatabase = new JdbcTemplate(dataSource);
        Flyway.configure().cleanDisabled(false).dataSource(dataSource).locations("classpath:db/migration")
                .target("3").load().migrate();
        SQLiteApplicantAccountRepository accounts = new SQLiteApplicantAccountRepository(populatedDatabase);
        ApplicantId owner = account(accounts, "migration@example.com");
        SQLiteKycApplicationRepository applications = new SQLiteKycApplicationRepository(populatedDatabase);
        SQLiteKycApplicationFormRepository forms = new SQLiteKycApplicationFormRepository(populatedDatabase);
        KycApplication draft = KycApplication.start(owner, NOW);
        applications.save(draft);
        ApplicationForm form = ApplicationForm.blank(draft.id(), NOW).update(ApplicationStep.PERSONAL_DETAILS,
                Map.of(FormField.NAME, "Existing draft"), null, NOW.plusSeconds(1));
        forms.save(form);
        new SQLitePrivacySafeApplicationAuditPublisher(populatedDatabase).publish(new PrivacySafeApplicationAuditEvent(
                ApplicationAuditEventType.APPLICATION_STARTED, "success", NOW, UUID.randomUUID(), UUID.randomUUID().toString()));

        Flyway.configure().cleanDisabled(false).dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(applications.findById(draft.id())).get().extracting(KycApplication::status)
                .isEqualTo(ApplicationStatus.DRAFT);
        assertThat(forms.findByApplicationId(draft.id())).get()
                .satisfies(persistedForm -> assertThat(persistedForm.answers().get(FormField.NAME)).isEqualTo("Existing draft"));
        assertThat(jdbcTemplateFor(dataSource).queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_STARTED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplateFor(dataSource).queryForList("PRAGMA foreign_key_check", Object.class)).isEmpty();
        assertThat(jdbcTemplateFor(dataSource).queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'kyc_applications_one_active_draft_per_owner_idx'",
                String.class)).containsExactly("kyc_applications_one_active_draft_per_owner_idx");
    }

    @Test
    void persistsSubmissionAndAuditAtomicallyAndRejectsReplayWrites() {
        ApplicantId owner = account("submit@example.com");
        KycApplication draft = KycApplication.start(owner, NOW);
        applicationRepository.save(draft);
        ApplicationForm form = completeForm(draft.id());
        jdbcTemplate.update("INSERT INTO kyc_application_forms (application_id, updated_at) VALUES (?, ?)",
                draft.id().value().toString(), NOW.toString());
        // Save through the repository one version at a time to retain its optimistic version contract.
        ApplicationForm persistedForm = ApplicationForm.blank(draft.id(), NOW);
        for (ApplicationStep step : ApplicationStep.values()) {
            persistedForm = persistedForm.update(step,
                    form.answers().entrySet().stream().filter(entry -> entry.getKey().step() == step)
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                    step == ApplicationStep.PERSONAL_DETAILS ? true : null, NOW.plusSeconds(step.ordinal() + 1));
            formRepository.save(persistedForm);
        }
        persistedForm = persistedForm.withDocumentEvidence(
                new DocumentStorageReference("evidence-key", "application/pdf", 10, "digest"), NOW.plusSeconds(3));
        formRepository.save(persistedForm);
        KycApplication submitted = draft.submit(owner, persistedForm, persistedForm.version(), NOW.plusSeconds(4));
        PrivacySafeApplicationAuditEvent event = new PrivacySafeApplicationAuditEvent(
                ApplicationAuditEventType.APPLICATION_SUBMITTED, "success", NOW.plusSeconds(4),
                UUID.randomUUID(), UUID.randomUUID().toString());

        assertThat(applicationRepository.commitSubmission(submitted, persistedForm.version(), event)).isTrue();
        assertThat(applicationRepository.findById(draft.id())).get().extracting(KycApplication::status)
                .isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_SUBMITTED'", Integer.class))
                .isEqualTo(1);
        assertThat(applicationRepository.commitSubmission(submitted, persistedForm.version(), event)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_SUBMITTED'", Integer.class))
                .isEqualTo(1);
        ApplicationForm submittedForm = persistedForm;
        assertThatThrownBy(() -> formRepository.save(submittedForm.update(
                ApplicationStep.PERSONAL_DETAILS, Map.of(FormField.NAME, "Changed"), null, NOW.plusSeconds(5))))
                .isInstanceOf(RuntimeException.class);
    }

    private ApplicantId account(final String email) {
        return account(accountRepository, email);
    }

    private static ApplicantId account(final SQLiteApplicantAccountRepository repository, final String email) {
        ApplicantAccount account = ApplicantAccount.register(
                new EmailAddress(email), new PasswordVerifier("argon2id-verifier"), NOW);
        repository.save(account);
        return new ApplicantId(account.id());
    }

    private static ApplicationForm completeForm(final ApplicationId applicationId) {
        Map<FormField, String> personal = new EnumMap<>(FormField.class);
        FormField.answerFields(ApplicationStep.PERSONAL_DETAILS).forEach(field -> personal.put(field, "present"));
        ApplicationForm form = ApplicationForm.blank(applicationId, NOW)
                .update(ApplicationStep.PERSONAL_DETAILS, personal, true, NOW.plusSeconds(1));
        Map<FormField, String> identity = new EnumMap<>(FormField.class);
        FormField.answerFields(ApplicationStep.IDENTITY_AND_ADDRESS).forEach(field -> identity.put(field, "present"));
        form = form.update(ApplicationStep.IDENTITY_AND_ADDRESS, identity, null, NOW.plusSeconds(2));
        return form.withDocumentEvidence(
                new DocumentStorageReference("evidence-key", "application/pdf", 10, "digest"), NOW.plusSeconds(3));
    }

    private static JdbcTemplate jdbcTemplateFor(final SQLiteDataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
