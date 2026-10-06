package com.kyc.identityaccess.adapter.persistence;

import com.kyc.identityaccess.application.port.out.ApplicantAccountRepository;
import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.EmailAddress;
import com.kyc.identityaccess.domain.PasswordVerifier;
import com.kyc.identityaccess.domain.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite implementation of the Applicant account persistence boundary. */
@Repository
public class SQLiteApplicantAccountRepository implements ApplicantAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Creates the adapter with the application-configured SQLite data source. */
    public SQLiteApplicantAccountRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByEmail(final EmailAddress email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applicant_accounts WHERE normalized_email = ?",
                Integer.class,
                email.value());
        return count != null && count > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ApplicantAccount> findByEmail(final EmailAddress email) {
        return jdbcTemplate.query(
                        """
                        SELECT id, normalized_email, password_verifier, role, created_at
                        FROM applicant_accounts
                        WHERE normalized_email = ?
                        """,
                        (resultSet, rowNumber) -> new ApplicantAccount(
                                UUID.fromString(resultSet.getString("id")),
                                new EmailAddress(resultSet.getString("normalized_email")),
                                new PasswordVerifier(resultSet.getString("password_verifier")),
                                Role.valueOf(resultSet.getString("role")),
                                Instant.parse(resultSet.getString("created_at"))),
                        email.value())
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void save(final ApplicantAccount account) {
        jdbcTemplate.update(
                """
                INSERT INTO applicant_accounts (
                    id, normalized_email, password_verifier, role, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                account.id().toString(),
                account.email().value(),
                account.passwordVerifier().encodedValue(),
                account.role().name(),
                account.createdAt().toString());
    }
}
