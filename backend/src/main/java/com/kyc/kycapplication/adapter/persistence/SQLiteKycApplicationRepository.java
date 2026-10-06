package com.kyc.kycapplication.adapter.persistence;

import com.kyc.kycapplication.application.port.out.ActiveDraftConflictException;
import com.kyc.kycapplication.application.port.out.ApplicationSubmissionRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStatus;
import com.kyc.kycapplication.domain.ApplicationStep;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.PrivacySafeApplicationAuditEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite adapter that delegates the one-active-draft invariant to the partial unique index. */
@Repository
public class SQLiteKycApplicationRepository implements KycApplicationRepository, ApplicationSubmissionRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the application-configured SQLite connection. */
    public SQLiteKycApplicationRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<KycApplication> findActiveByOwner(final ApplicantId ownerId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, applicant_account_id, lifecycle, current_step, created_at, updated_at, submitted_at,
                               approved_at
                        FROM kyc_applications
                        WHERE applicant_account_id = ? AND lifecycle = 'DRAFT'
                        """,
                        (resultSet, rowNumber) -> new KycApplication(
                                new ApplicationId(UUID.fromString(resultSet.getString("id"))),
                                new ApplicantId(UUID.fromString(resultSet.getString("applicant_account_id"))),
                                ApplicationStatus.valueOf(resultSet.getString("lifecycle")),
                                ApplicationStep.valueOf(resultSet.getString("current_step")),
                                Instant.parse(resultSet.getString("created_at")),
                                Instant.parse(resultSet.getString("updated_at")),
                                instantValue(resultSet.getString("submitted_at")),
                                instantValue(resultSet.getString("approved_at"))),
                        ownerId.value().toString())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<KycApplication> findById(final ApplicationId applicationId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, applicant_account_id, lifecycle, current_step, created_at, updated_at, submitted_at,
                               approved_at
                        FROM kyc_applications WHERE id = ?
                        """,
                        (resultSet, rowNumber) -> new KycApplication(
                                new ApplicationId(UUID.fromString(resultSet.getString("id"))),
                                new ApplicantId(UUID.fromString(resultSet.getString("applicant_account_id"))),
                                ApplicationStatus.valueOf(resultSet.getString("lifecycle")),
                                ApplicationStep.valueOf(resultSet.getString("current_step")),
                                Instant.parse(resultSet.getString("created_at")),
                                Instant.parse(resultSet.getString("updated_at")),
                                instantValue(resultSet.getString("submitted_at")),
                                instantValue(resultSet.getString("approved_at"))),
                        applicationId.value().toString())
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void save(final KycApplication application) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO kyc_applications (
                        id, applicant_account_id, lifecycle, current_step, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    application.id().value().toString(),
                    application.ownerId().value().toString(),
                    application.status().name(),
                    application.currentStep().name(),
                    application.createdAt().toString(),
                    application.updatedAt().toString());
        } catch (DataAccessException exception) {
            if (isActiveDraftConstraint(exception)) {
                throw new ActiveDraftConflictException(exception);
            }
            throw exception;
        }
    }

    /** Updates the aggregate's current-step projection after an accepted form save. */
    @Transactional
    public void updateCurrentStep(final ApplicationId applicationId, final ApplicationStep currentStep,
                                  final Instant updatedAt) {
        int updated = jdbcTemplate.update(
                "UPDATE kyc_applications SET current_step = ?, updated_at = ? WHERE id = ? AND lifecycle = 'DRAFT'",
                currentStep.name(), updatedAt.toString(), applicationId.value().toString());
        if (updated == 0) {
            throw new IllegalStateException("Application is no longer an editable draft.");
        }
    }

    @Override
    @Transactional
    public boolean commitSubmission(
            final KycApplication submittedApplication,
            final int expectedFormVersion,
            final PrivacySafeApplicationAuditEvent auditEvent) {
        int updated = jdbcTemplate.update(
                """
                UPDATE kyc_applications
                SET lifecycle = 'SUBMITTED', submitted_at = ?, updated_at = ?
                WHERE id = ? AND lifecycle = 'DRAFT' AND EXISTS (
                    SELECT 1 FROM kyc_application_forms form
                    WHERE form.application_id = kyc_applications.id AND form.version = ?
                        AND form.name IS NOT NULL AND TRIM(form.name) <> ''
                        AND form.date_of_birth IS NOT NULL AND TRIM(form.date_of_birth) <> ''
                        AND form.country IS NOT NULL AND TRIM(form.country) <> ''
                        AND form.nationality IS NOT NULL AND TRIM(form.nationality) <> ''
                        AND form.email IS NOT NULL AND TRIM(form.email) <> ''
                        AND form.phone IS NOT NULL AND TRIM(form.phone) <> ''
                        AND form.consent_confirmed = 1
                        AND form.document_type IS NOT NULL AND TRIM(form.document_type) <> ''
                        AND form.document_number IS NOT NULL AND TRIM(form.document_number) <> ''
                        AND form.document_country IS NOT NULL AND TRIM(form.document_country) <> ''
                        AND form.expiry IS NOT NULL AND TRIM(form.expiry) <> ''
                        AND form.street IS NOT NULL AND TRIM(form.street) <> ''
                        AND form.city IS NOT NULL AND TRIM(form.city) <> ''
                        AND form.postal IS NOT NULL AND TRIM(form.postal) <> ''
                        AND form.residential_country IS NOT NULL AND TRIM(form.residential_country) <> ''
                        AND form.document_evidence_present = 1
                )
                """,
                submittedApplication.submittedAt().toString(), submittedApplication.updatedAt().toString(),
                submittedApplication.id().value().toString(), expectedFormVersion);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update(
                """
                INSERT INTO kyc_application_audit_events (
                    event_type, outcome, occurred_at, correlation_id, minimized_subject_reference
                ) VALUES (?, ?, ?, ?, ?)
                """,
                auditEvent.type().name(), auditEvent.outcome(), auditEvent.occurredAt().toString(),
                auditEvent.correlationId().toString(), auditEvent.minimizedSubjectReference());
        return true;
    }

    private static boolean isActiveDraftConstraint(final DataAccessException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("UNIQUE constraint failed: kyc_applications.applicant_account_id");
    }

    private static Instant instantValue(final String value) {
        return value == null ? null : Instant.parse(value);
    }
}
