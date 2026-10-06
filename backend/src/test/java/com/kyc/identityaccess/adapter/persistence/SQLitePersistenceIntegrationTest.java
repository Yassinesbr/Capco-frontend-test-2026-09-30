package com.kyc.identityaccess.adapter.persistence;

import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.AuditEventType;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import com.kyc.identityaccess.domain.Role;
import com.kyc.identityaccess.domain.SessionId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLitePersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-22T12:00:00Z");

    @TempDir
    private Path databaseDirectory;

    private JdbcTemplate jdbcTemplate;
    private SQLiteApplicantAccountRepository accountRepository;
    private SQLiteApplicantSessionRepository sessionRepository;
    private SQLitePrivacySafeAuditEventPublisher auditEventPublisher;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void migrateCleanSqliteDatabase() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve("identity-access.db").toAbsolutePath());
        dataSource.setEnforceForeignKeys(true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        Flyway.configure()
                .cleanDisabled(false)
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        accountRepository = new SQLiteApplicantAccountRepository(jdbcTemplate);
        sessionRepository = new SQLiteApplicantSessionRepository(jdbcTemplate);
        auditEventPublisher = new SQLitePrivacySafeAuditEventPublisher(jdbcTemplate);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void migratesAEmptyDatabaseToTheVersionedIdentityAccessSchema() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", String.class);

        assertThat(tableNames).contains(
                "applicant_accounts",
                "applicant_sessions",
                "flyway_schema_history",
                "identity_access_audit_events",
                "kyc_applications",
                "kyc_application_audit_events",
                "kyc_application_forms");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(6);
    }

    @Test
    void enforcesNormalizedEmailUniquenessAtTheDatabaseBoundary() {
        accountRepository.save(account("applicant@example.com"));

        assertThatThrownBy(() -> accountRepository.save(account("applicant@example.com")))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applicant_accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void enforcesTheSessionToApplicantForeignKeyConstraint() {
        ApplicantAccount account = account("applicant@example.com");

        assertThatThrownBy(() -> sessionRepository.save(session(
                account, new SessionId("orphaned-opaque-session"),
                CREATED_AT.plusSeconds(60), CREATED_AT.plusSeconds(120))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rollsBackAccountWritesWhenTheEnclosingPersistenceOperationFails() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            accountRepository.save(account("applicant@example.com"));
            jdbcTemplate.update("INSERT INTO applicant_accounts (id) VALUES (?)", UUID.randomUUID().toString());
        })).isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applicant_accounts", Integer.class)).isZero();
    }

    @Test
    void findsOnlyUnrevokedAndUnexpiredSessionsWithoutPersistingTheirRawIds() {
        ApplicantAccount account = account("applicant@example.com");
        accountRepository.save(account);
        SessionId activeId = new SessionId("active-opaque-session");
        SessionId expiredId = new SessionId("expired-opaque-session");
        SessionId revokedId = new SessionId("revoked-opaque-session");
        Instant lookupTime = CREATED_AT.plusSeconds(120);
        sessionRepository.save(session(account, activeId, CREATED_AT.plusSeconds(180), CREATED_AT.plusSeconds(240)));
        sessionRepository.save(session(account, expiredId, CREATED_AT.plusSeconds(60), CREATED_AT.plusSeconds(240)));
        sessionRepository.save(session(account, revokedId, CREATED_AT.plusSeconds(180), CREATED_AT.plusSeconds(240)));
        sessionRepository.revoke(revokedId, CREATED_AT.plusSeconds(1));

        assertThat(sessionRepository.findActiveById(activeId, lookupTime)).isPresent();
        assertThat(sessionRepository.findActiveById(expiredId, lookupTime)).isEmpty();
        assertThat(sessionRepository.findActiveById(revokedId, lookupTime)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT group_concat(session_id_hash) FROM applicant_sessions", String.class))
                .doesNotContain(activeId.value(), expiredId.value(), revokedId.value());
    }

    @Test
    void persistsOnlyPrivacySafeAuditFields() {
        PrivacySafeAuditEvent event = new PrivacySafeAuditEvent(
                AuditEventType.APPLICANT_REGISTERED,
                "success",
                CREATED_AT,
                UUID.randomUUID(),
                UUID.randomUUID().toString());

        auditEventPublisher.publish(event);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_access_audit_events "
                        + "WHERE event_type = ? AND outcome = ?",
                Integer.class,
                "APPLICANT_REGISTERED",
                "success"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM pragma_table_info('identity_access_audit_events')", String.class))
                .noneMatch(column -> column.contains("password")
                        || column.contains("session") || column.contains("token"));
    }

    private static ApplicantAccount account(final String email) {
        return ApplicantAccount.register(
                new EmailAddress(email), new PasswordVerifier("argon2id-verifier"), CREATED_AT);
    }

    private static ApplicantSession session(
            final ApplicantAccount account,
            final SessionId sessionId,
            final Instant idleExpiresAt,
            final Instant absoluteExpiresAt) {
        return new ApplicantSession(
                sessionId,
                account.id(),
                Role.APPLICANT,
                CREATED_AT,
                idleExpiresAt,
                absoluteExpiresAt,
                null);
    }
}
