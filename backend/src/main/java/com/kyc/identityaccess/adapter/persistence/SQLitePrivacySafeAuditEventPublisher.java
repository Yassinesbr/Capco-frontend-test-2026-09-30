package com.kyc.identityaccess.adapter.persistence;

import com.kyc.identityaccess.application.port.out.PrivacySafeAuditEventPublisher;
import com.kyc.identityaccess.domain.PrivacySafeAuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite publisher that records only privacy-safe audit facts. */
@Repository
public class SQLitePrivacySafeAuditEventPublisher implements PrivacySafeAuditEventPublisher {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the application-configured SQLite data source. */
    public SQLitePrivacySafeAuditEventPublisher(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void publish(final PrivacySafeAuditEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO identity_access_audit_events (
                    event_type, outcome, occurred_at, correlation_id, minimized_subject_reference
                ) VALUES (?, ?, ?, ?, ?)
                """,
                event.type().name(),
                event.outcome(),
                event.occurredAt().toString(),
                event.correlationId().toString(),
                event.minimizedSubjectReference());
    }
}
