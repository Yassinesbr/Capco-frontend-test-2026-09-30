package com.kyc.identityaccess.adapter.persistence;

import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.domain.ApplicantSession;
import com.kyc.identityaccess.domain.Role;
import com.kyc.identityaccess.domain.SessionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite implementation that stores only a one-way session lookup value. */
@Repository
public class SQLiteApplicantSessionRepository implements ApplicantSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the application-configured SQLite data source. */
    public SQLiteApplicantSessionRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(final ApplicantSession session) {
        Object[] values = {
                lookupHash(session.id()),
                session.applicantAccountId().toString(),
                session.role().name(),
                session.createdAt().toString(),
                session.idleExpiresAt().toString(),
                session.absoluteExpiresAt().toString(),
                instantValue(session.revokedAt())
        };
        if (session.role() == Role.APPLICANT) {
            jdbcTemplate.update("""
                    INSERT INTO applicant_sessions (
                        session_id_hash, applicant_account_id, role, created_at,
                        idle_expires_at, absolute_expires_at, revoked_at, rotated_from_session_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, values[0], values[1], values[2], values[3], values[4], values[5], values[6], null);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO internal_staff_sessions (
                        session_id_hash, staff_account_id, role, created_at,
                        idle_expires_at, absolute_expires_at, revoked_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, values);
        }
    }

    @Override
    public Optional<ApplicantSession> findActiveById(final SessionId sessionId, final Instant instant) {
        removeExpiredSessions(instant);
        return jdbcTemplate.query(
                        """
                        SELECT applicant_account_id, role, created_at, idle_expires_at,
                               absolute_expires_at, revoked_at
                        FROM applicant_sessions
                        WHERE session_id_hash = ?
                          AND revoked_at IS NULL
                          AND idle_expires_at > ?
                          AND absolute_expires_at > ?
                        UNION ALL
                        SELECT staff_account_id AS applicant_account_id, role, created_at, idle_expires_at,
                               absolute_expires_at, revoked_at
                        FROM internal_staff_sessions
                        WHERE session_id_hash = ? AND revoked_at IS NULL
                          AND idle_expires_at > ? AND absolute_expires_at > ?
                        """,
                        (resultSet, rowNumber) -> new ApplicantSession(
                                sessionId,
                                UUID.fromString(resultSet.getString("applicant_account_id")),
                                Role.valueOf(resultSet.getString("role")),
                                Instant.parse(resultSet.getString("created_at")),
                                Instant.parse(resultSet.getString("idle_expires_at")),
                                Instant.parse(resultSet.getString("absolute_expires_at")),
                                null),
                        lookupHash(sessionId),
                        instant.toString(),
                        instant.toString(),
                        lookupHash(sessionId),
                        instant.toString(),
                        instant.toString())
                .stream()
                .findFirst();
    }

    /** Removes expired opaque-session lookup records as part of normal session access. */
    private void removeExpiredSessions(final Instant instant) {
        jdbcTemplate.update(
                "DELETE FROM applicant_sessions WHERE idle_expires_at <= ? OR absolute_expires_at <= ?",
                instant.toString(),
                instant.toString());
        jdbcTemplate.update(
                "DELETE FROM internal_staff_sessions WHERE idle_expires_at <= ? OR absolute_expires_at <= ?",
                instant.toString(),
                instant.toString());
    }

    @Override
    @Transactional
    public void revoke(final SessionId sessionId, final Instant revokedAt) {
        jdbcTemplate.update(
                "UPDATE applicant_sessions SET revoked_at = ? "
                        + "WHERE session_id_hash = ? AND revoked_at IS NULL",
                revokedAt.toString(),
                lookupHash(sessionId));
        jdbcTemplate.update(
                "UPDATE internal_staff_sessions SET revoked_at = ? "
                        + "WHERE session_id_hash = ? AND revoked_at IS NULL",
                revokedAt.toString(),
                lookupHash(sessionId));
    }

    private static String instantValue(final Instant value) {
        return value == null ? null : value.toString();
    }

    private static String lookupHash(final SessionId sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.value().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
