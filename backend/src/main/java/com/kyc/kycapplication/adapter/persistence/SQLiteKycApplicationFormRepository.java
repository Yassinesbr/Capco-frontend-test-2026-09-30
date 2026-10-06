package com.kyc.kycapplication.adapter.persistence;

import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.FormField;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite adapter for the atomic Applicant form projection. */
@Repository
public class SQLiteKycApplicationFormRepository implements KycApplicationFormRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the configured SQLite connection. */
    public SQLiteKycApplicationFormRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ApplicationForm> findByApplicationId(final ApplicationId applicationId) {
        return jdbcTemplate.query(
                        "SELECT * FROM kyc_application_forms WHERE application_id = ?",
                        (resultSet, rowNumber) -> map(resultSet), applicationId.value().toString())
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void save(final ApplicationForm form) {
        Map<String, Object> values = values(form);
        int updated = jdbcTemplate.update(
                """
                UPDATE kyc_application_forms SET version = ?, name = ?, date_of_birth = ?, country = ?,
                    nationality = ?, email = ?, phone = ?, consent_confirmed = ?, document_type = ?,
                    document_number = ?, document_country = ?, expiry = ?, street = ?, city = ?, postal = ?,
                    residential_country = ?, document_evidence_present = ?, document_storage_key = ?,
                    document_media_type = ?, document_size = ?, document_digest = ?, updated_at = ?
                WHERE application_id = ? AND version = ?
                """,
                values.get("version"), values.get("name"), values.get("date_of_birth"), values.get("country"),
                values.get("nationality"), values.get("email"), values.get("phone"), values.get("consent"),
                values.get("document_type"), values.get("document_number"), values.get("document_country"),
                values.get("expiry"), values.get("street"), values.get("city"), values.get("postal"),
                values.get("residential_country"), values.get("evidence"), values.get("storage_key"),
                values.get("media_type"), values.get("size"), values.get("digest"), values.get("updated_at"),
                form.applicationId().value().toString(), form.version() - 1);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO kyc_application_forms (application_id, version, name, date_of_birth, country,
                        nationality, email, phone, consent_confirmed, document_type, document_number,
                        document_country, expiry, street, city, postal, residential_country,
                        document_evidence_present, document_storage_key, document_media_type, document_size,
                        document_digest, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    form.applicationId().value().toString(), values.get("version"), values.get("name"),
                    values.get("date_of_birth"), values.get("country"), values.get("nationality"),
                    values.get("email"), values.get("phone"), values.get("consent"), values.get("document_type"),
                    values.get("document_number"), values.get("document_country"), values.get("expiry"),
                    values.get("street"), values.get("city"), values.get("postal"),
                    values.get("residential_country"), values.get("evidence"), values.get("storage_key"),
                    values.get("media_type"), values.get("size"), values.get("digest"), values.get("updated_at"));
        }
        int applicationUpdated = jdbcTemplate.update(
                "UPDATE kyc_applications SET current_step = ?, updated_at = ? WHERE id = ? AND lifecycle = 'DRAFT'",
                form.currentStep().name(), form.updatedAt().toString(), form.applicationId().value().toString());
        if (applicationUpdated == 0) {
            throw new IllegalStateException("Application is no longer an editable draft.");
        }
    }

    private static ApplicationForm map(final ResultSet resultSet) throws SQLException {
        Map<FormField, String> answers = new EnumMap<>(FormField.class);
        for (FormField field : FormField.values()) {
            if (field == FormField.CONSENT || field == FormField.DOCUMENT_EVIDENCE) {
                continue;
            }
            String column = column(field);
            String value = resultSet.getString(column);
            if (value != null) {
                answers.put(field, value);
            }
        }
        return ApplicationForm.rehydrate(
                new ApplicationId(java.util.UUID.fromString(resultSet.getString("application_id"))), answers,
                resultSet.getInt("consent_confirmed") == 1,
                resultSet.getInt("document_evidence_present") == 1,
                resultSet.getString("document_storage_key"), resultSet.getString("document_media_type"),
                nullableLong(resultSet, "document_size"), resultSet.getString("document_digest"),
                resultSet.getInt("version"), java.time.Instant.parse(resultSet.getString("updated_at")));
    }

    private static Long nullableLong(final ResultSet resultSet, final String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Map<String, Object> values(final ApplicationForm form) {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("version", form.version());
        values.put("consent", form.consentConfirmed() ? 1 : 0);
        values.put("evidence", form.documentEvidencePresent() ? 1 : 0);
        values.put("storage_key", form.documentStorageKey());
        values.put("media_type", form.documentMediaType());
        values.put("size", form.documentSize());
        values.put("digest", form.documentDigest());
        values.put("updated_at", form.updatedAt().toString());
        for (FormField field : FormField.values()) {
            if (field == FormField.CONSENT || field == FormField.DOCUMENT_EVIDENCE) {
                continue;
            }
            values.put(column(field), form.answers().get(field));
        }
        return values;
    }

    private static String column(final FormField field) {
        return switch (field) {
            case NAME -> "name";
            case DATE_OF_BIRTH -> "date_of_birth";
            case COUNTRY -> "country";
            case NATIONALITY -> "nationality";
            case EMAIL -> "email";
            case PHONE -> "phone";
            case DOCUMENT_TYPE -> "document_type";
            case DOCUMENT_NUMBER -> "document_number";
            case DOCUMENT_COUNTRY -> "document_country";
            case EXPIRY -> "expiry";
            case STREET -> "street";
            case CITY -> "city";
            case POSTAL -> "postal";
            case RESIDENTIAL_COUNTRY -> "residential_country";
            case CONSENT, DOCUMENT_EVIDENCE -> throw new IllegalArgumentException("non-answer field");
        };
    }
}
