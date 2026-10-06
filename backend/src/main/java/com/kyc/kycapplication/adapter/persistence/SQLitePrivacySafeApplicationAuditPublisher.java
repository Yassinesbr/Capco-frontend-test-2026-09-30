package com.kyc.kycapplication.adapter.persistence;

import com.kyc.kycapplication.application.port.out.PrivacySafeApplicationAuditPublisher;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite publisher that persists only KYC Application privacy-safe audit fields. */
@Repository
public class SQLitePrivacySafeApplicationAuditPublisher implements PrivacySafeApplicationAuditPublisher {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the application-configured SQLite connection. */
    public SQLitePrivacySafeApplicationAuditPublisher(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void publish(final PrivacySafeApplicationAuditEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO kyc_application_audit_events (
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
