package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.bootstrap.IdentityAccessApplication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies reviewer overview roles, denied identities, minimized projection, and UTC metrics. */
@SpringBootTest(classes = IdentityAccessApplication.class)
@AutoConfigureMockMvc
class ReviewerApplicationsHttpIntegrationTest {
    private static final java.nio.file.Path DATABASE = databasePath();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM kyc_application_forms");
        jdbcTemplate.update("DELETE FROM kyc_application_audit_events");
        jdbcTemplate.update("DELETE FROM kyc_applications");
        jdbcTemplate.update("DELETE FROM applicant_sessions");
        jdbcTemplate.update("DELETE FROM internal_staff_sessions");
        jdbcTemplate.update("DELETE FROM applicant_accounts");
        String applicantId = seedApplications();
        seedSession("reviewer-token", "REVIEWER", UUID.randomUUID().toString());
        seedSession("administrator-token", "ADMINISTRATOR", UUID.randomUUID().toString());
        seedSession("applicant-token", "APPLICANT", applicantId);
    }

    @Test
    void anonymousAndApplicantAreDeniedWhileReviewerAndAdministratorCanReadMinimizedOverview() throws Exception {
        mockMvc.perform(get("/api/v1/reviewer-applications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reviewer-applications").cookie(cookie("applicant-token")))
                .andExpect(status().isForbidden());
        for (String token : new String[] {"reviewer-token", "administrator-token"}) {
            mockMvc.perform(get("/api/v1/reviewer-applications").cookie(cookie(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.summary.pendingReview").value(2))
                    .andExpect(jsonPath("$.meta.summary.inProgress").value(1))
                    .andExpect(jsonPath("$.meta.summary.approvedThisMonth").value(1))
                    .andExpect(jsonPath("$.data.length()").value(4))
                    .andExpect(jsonPath("$.data[0].attributes.dateOfBirth").doesNotExist());
        }
    }

    @Test
    void searchAndStatusFilterApplyBeforePagination() throws Exception {
        mockMvc.perform(get("/api/v1/reviewer-applications").cookie(cookie("reviewer-token"))
                        .param("search", "ada").param("status", "in-review").param("page-size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].attributes.applicantName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data[0].attributes.status").value("in-review"));
        mockMvc.perform(get("/api/v1/reviewer-applications").cookie(cookie("reviewer-token"))
                        .param("status", "unknown"))
                .andExpect(status().isBadRequest());
    }

    private String seedApplications() {
        String ownerId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update("INSERT INTO applicant_accounts VALUES (?, ?, ?, 'APPLICANT', ?)", ownerId,
                "overview@example.test", "test-verifier", now.toString());
        addApplication(ownerId, "draft-id", "DRAFT", "Draft User", null, null);
        addApplication(ownerId, "submitted-id", "SUBMITTED", "Submitted User", now, null);
        addApplication(ownerId, "inreview-id", "IN_REVIEW", "Ada Lovelace", now.minusSeconds(3600), null);
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        addApplication(ownerId, "approved-id", "APPROVED", "Approved User", now.minusSeconds(7200), monthStart.plusSeconds(1));
        return ownerId;
    }

    private void addApplication(String ownerId, String id, String status, String name, Instant submittedAt,
            Instant approvedAt) {
        if (!id.equals("draft-id")) {
            ownerId = UUID.randomUUID().toString();
            jdbcTemplate.update("INSERT INTO applicant_accounts VALUES (?, ?, ?, 'APPLICANT', ?)", ownerId,
                    id + "@example.test", "test-verifier", Instant.now().toString());
        }
        Instant createdAt = submittedAt == null ? Instant.now().minusSeconds(10000) : submittedAt.minusSeconds(60);
        Instant updatedAt = approvedAt == null ? (submittedAt == null ? createdAt : submittedAt) : approvedAt;
        jdbcTemplate.update("""
                INSERT INTO kyc_applications (id, applicant_account_id, lifecycle, current_step, created_at,
                    updated_at, submitted_at, approved_at) VALUES (?, ?, 'DRAFT', 'PERSONAL_DETAILS', ?, ?, NULL, NULL)
                """, id, ownerId, createdAt.toString(), createdAt.toString());
        jdbcTemplate.update("INSERT INTO kyc_application_forms (application_id, name, date_of_birth, updated_at) "
                + "VALUES (?, ?, ?, ?)", id, name, "SENSITIVE-DOB", updatedAt.toString());
        jdbcTemplate.update("UPDATE kyc_applications SET lifecycle = ?, updated_at = ?, submitted_at = ?, approved_at = ? "
                        + "WHERE id = ?", status, updatedAt.toString(),
                submittedAt == null ? null : submittedAt.toString(), approvedAt == null ? null : approvedAt.toString(), id);
    }

    private void seedSession(String rawToken, String role, String accountId) {
        String hash = hash(rawToken);
        Instant now = Instant.now();
        String table = role.equals("APPLICANT") ? "applicant_sessions" : "internal_staff_sessions";
        String accountColumn = role.equals("APPLICANT") ? "applicant_account_id" : "staff_account_id";
        jdbcTemplate.update("INSERT INTO " + table + " (session_id_hash, " + accountColumn
                        + ", role, created_at, idle_expires_at, absolute_expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                hash, accountId, role, now.toString(), now.plusSeconds(3600).toString(), now.plusSeconds(7200).toString());
    }

    private static MockCookie cookie(String token) {
        return new MockCookie("__Host-KYCSESSION", token);
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static java.nio.file.Path databasePath() {
        try {
            java.nio.file.Path path = java.nio.file.Files.createTempFile("reviewer-overview-", ".db");
            java.nio.file.Files.deleteIfExists(path);
            path.toFile().deleteOnExit();
            return path;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
