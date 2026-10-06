package com.kyc.kycapplication.adapter.persistence;

import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository.ReviewerApplicationPage;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository.ReviewerApplicationRow;
import com.kyc.kycapplication.domain.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQLite projection for the protected staff overview; never selects form answers or documents. */
@Repository
public class SQLiteReviewerApplicationQueryRepository implements ReviewerApplicationQueryRepository {
    private final JdbcTemplate jdbcTemplate;

    public SQLiteReviewerApplicationQueryRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReviewerApplicationPage find(final String search, final ApplicationStatus status, final int page,
            final int pageSize, final Instant monthStart, final Instant monthEndExclusive) {
        String searchPattern = "%" + search.toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String statusValue = status == null ? null : status.name();
        String where = "(? = '' OR LOWER(COALESCE(form.name, '')) LIKE ? ESCAPE '!' "
                + "OR LOWER(app.id) LIKE ? ESCAPE '!') AND (? IS NULL OR app.lifecycle = ?)";
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_applications app LEFT JOIN kyc_application_forms form "
                        + "ON form.application_id = app.id WHERE " + where,
                Long.class, search, searchPattern, searchPattern, statusValue, statusValue);
        List<ReviewerApplicationRow> rows = jdbcTemplate.query(
                """
                SELECT app.id, COALESCE(form.name, '') AS applicant_name, app.lifecycle, app.submitted_at
                FROM kyc_applications app
                LEFT JOIN kyc_application_forms form ON form.application_id = app.id
                WHERE %s
                ORDER BY julianday(COALESCE(app.submitted_at, app.created_at)) DESC, app.created_at DESC, app.id ASC
                LIMIT ? OFFSET ?
                """.formatted(where),
                (resultSet, rowNumber) -> new ReviewerApplicationRow(
                        resultSet.getString("id"), resultSet.getString("applicant_name"),
                        instantValue(resultSet.getString("submitted_at")),
                        ApplicationStatus.valueOf(resultSet.getString("lifecycle"))),
                search, searchPattern, searchPattern, statusValue, statusValue, pageSize, (long) page * pageSize);
        int pending = count("lifecycle IN ('SUBMITTED', 'IN_REVIEW')", null, null);
        int inProgress = count("lifecycle = 'DRAFT'", null, null);
        int approved = count("lifecycle = 'APPROVED' AND julianday(approved_at) >= julianday(?) "
                        + "AND julianday(approved_at) < julianday(?)",
                monthStart.toString(), monthEndExclusive.toString());
        return new ReviewerApplicationPage(rows, pending, inProgress, approved, total == null ? 0 : total);
    }

    private int count(final String condition, final String first, final String second) {
        Long count = first == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kyc_applications WHERE " + condition, Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kyc_applications WHERE " + condition,
                        Long.class, first, second);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private static Instant instantValue(final String value) {
        return value == null ? null : Instant.parse(value);
    }
}
