package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.bootstrap.IdentityAccessApplication;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies owner-scoped application status and deliberate submission through HTTP and SQLite. */
@SpringBootTest(classes = IdentityAccessApplication.class)
@AutoConfigureMockMvc
class ApplicantApplicationSubmissionHttpIntegrationTest {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final Path DATABASE_PATH = databasePath();
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger(90);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void sqliteProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_PATH);
    }

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM kyc_application_audit_events");
        jdbcTemplate.update("DELETE FROM kyc_applications");
        jdbcTemplate.update("DELETE FROM identity_access_audit_events");
        jdbcTemplate.update("DELETE FROM applicant_sessions");
        jdbcTemplate.update("DELETE FROM applicant_accounts");
    }

    @Test
    @DisplayName("Given Applicant B is authenticated, when B requests A's application, then access is undisclosed")
    void statusAndSubmissionRequireAuthenticationAndHideCrossOwnerApplications() throws Exception {
        Applicant applicant = createApplicant("owner@example.com");
        Applicant other = createApplicant("other@example.com");
        String applicationId = startApplication(applicant);

        mockMvc.perform(get("/api/v1/applicant-applications/{applicationId}", applicationId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"));
        mockMvc.perform(get("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(other.sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("application-not-found"));
        mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(other.sessionCookie(), other.csrfCookie())
                        .header("X-XSRF-TOKEN", other.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given a ready draft, when its owner submits and retries, then one persisted receipt is returned")
    void rejectsIncompleteAndStaleSubmissionsThenReturnsSameReceiptOnReplay() throws Exception {
        Applicant applicant = createApplicant("submit@example.com");
        String applicationId = startApplication(applicant);

        mockMvc.perform(get("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("draft"))
                .andExpect(jsonPath("$.data.attributes.receipt").doesNotExist());
        mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("required-fields-missing"))
                .andExpect(jsonPath("$.errors[0].detail").value(org.hamcrest.Matchers.containsString("name")))
                .andExpect(jsonPath("$.errors[0].detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private fixture"))));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT lifecycle FROM kyc_applications WHERE id = ?", String.class, applicationId)).isEqualTo("DRAFT");

        makeFormReady(applicationId);
        mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("form-version-conflict"));

        MvcResult submitted = mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"))
                .andExpect(jsonPath("$.data.attributes.status").value("submitted"))
                .andExpect(jsonPath("$.data.attributes.submittedAt").isString())
                .andExpect(jsonPath("$.data.attributes.receipt.message").value("Your application was received"))
                .andExpect(jsonPath("$.data.attributes.receipt.nextStep")
                        .value("The review team will review your application."))
                .andReturn();
        String submittedAt = value(submitted, "submittedAt");

        mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.submittedAt").value(submittedAt));
        mockMvc.perform(get("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("submitted"))
                .andExpect(jsonPath("$.data.attributes.receipt.message").value("Your application was received"));
        mockMvc.perform(get("/api/v1/applicant-applications/{applicationId}/form", applicationId)
                        .cookie(applicant.sessionCookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/applicant-applications/{applicationId}/document-evidence", applicationId)
                        .file(new MockMultipartFile("file", "replacement.png", "image/png", new byte[] {1, 2, 3}))
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_SUBMITTED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Given persistence fails, when the owner submits, then draft and audit state roll back")
    void persistenceFailureLeavesTheDraftUnchangedAndReturnsASafeError() throws Exception {
        Applicant applicant = createApplicant("failure@example.com");
        String applicationId = startApplication(applicant);
        makeFormReady(applicationId);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_submission_audit
                BEFORE INSERT ON kyc_application_audit_events
                WHEN NEW.event_type = 'APPLICATION_SUBMITTED'
                BEGIN SELECT RAISE(ABORT, 'forced persistence failure'); END
                """);

        MvcResult response = mockMvc.perform(patch("/api/v1/applicant-applications/{applicationId}", applicationId)
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken())
                        .contentType(JSON_API)
                        .content(submission(applicationId, 0)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"))
                .andExpect(jsonPath("$.errors[0].code").value("submission-unavailable"))
                .andExpect(jsonPath("$.errors[0].detail").value("Your application was not submitted. Please retry later."))
                .andReturn();

        assertThat(response.getResponse().getContentAsString())
                .doesNotContain("Private fixture", "private-storage-key", "forced persistence failure");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT lifecycle FROM kyc_applications WHERE id = ?", String.class, applicationId)).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT submitted_at FROM kyc_applications WHERE id = ?", String.class, applicationId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_application_audit_events WHERE event_type = 'APPLICATION_SUBMITTED'", Integer.class))
                .isZero();
    }

    private Applicant createApplicant(final String email) throws Exception {
        mockMvc.perform(post("/api/v1/applicant-accounts").contentType(JSON_API)
                        .content("{\"data\":{\"type\":\"applicant-accounts\",\"attributes\":{\"email\":\""
                                + email + "\",\"password\":\"Secure!1\"}}}"))
                .andExpect(status().isCreated());
        MvcResult signedIn = mockMvc.perform(post("/api/v1/applicant-sessions").contentType(JSON_API)
                        .content("{\"data\":{\"type\":\"applicant-sessions\",\"attributes\":{\"email\":\""
                                + email + "\",\"password\":\"Secure!1\"}}}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Applicant(cookie(signedIn, "__Host-KYCSESSION"), cookie(signedIn, "XSRF-TOKEN"),
                signedIn.getResponse().getHeader("X-CSRF-TOKEN"));
    }

    private String startApplication(final Applicant applicant) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/applicant-applications")
                        .cookie(applicant.sessionCookie(), applicant.csrfCookie())
                        .header("X-XSRF-TOKEN", applicant.csrfToken()))
                .andExpect(status().isCreated())
                .andReturn();
        return value(result, "id");
    }

    private void makeFormReady(final String applicationId) {
        jdbcTemplate.update("""
                INSERT INTO kyc_application_forms (
                    application_id, version, name, date_of_birth, country, nationality, email, phone,
                    consent_confirmed, document_type, document_number, document_country, expiry,
                    street, city, postal, residential_country, document_evidence_present,
                    document_storage_key, document_media_type, document_size, document_digest, updated_at
                ) VALUES (?, 0, 'Private fixture name', '1992-05-12', 'HU', 'HU', 'private@example.com', '123',
                    1, 'passport', 'AA1234567', 'HU', '2030-01-01', 'Private fixture street', 'Budapest',
                    '1000', 'HU', 1, 'private-storage-key', 'image/jpeg', 10, 'private-digest', ?)
                """, applicationId, NOW_TEXT);
    }

    private static String submission(final String applicationId, final int expectedFormVersion) {
        return "{\"data\":{\"type\":\"applicant-applications\",\"id\":\"" + applicationId
                + "\",\"attributes\":{\"status\":\"submitted\",\"confirmed\":true,\"expectedFormVersion\":"
                + expectedFormVersion + "}}}";
    }

    private static jakarta.servlet.http.Cookie cookie(final MvcResult response, final String name) {
        HttpCookie parsed = response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(HttpCookie::parse).flatMap(List::stream).filter(cookie -> name.equals(cookie.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("Expected response cookie " + name));
        return new jakarta.servlet.http.Cookie(parsed.getName(), parsed.getValue());
    }

    private static String value(final MvcResult response, final String property) throws Exception {
        String marker = "\"" + property + "\":\"";
        String body = response.getResponse().getContentAsString();
        int start = body.indexOf(marker);
        if (start < 0) throw new AssertionError("Expected JSON field " + property);
        int valueStart = start + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        if (valueEnd < 0) throw new AssertionError("Expected string JSON field " + property);
        return body.substring(valueStart, valueEnd);
    }

    private static Path databasePath() {
        try {
            Path path = Files.createTempFile("kyc-submission-http-", ".db");
            Files.deleteIfExists(path);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare isolated SQLite test database", exception);
        }
    }

    private static final String NOW_TEXT = "2026-09-24T12:00:00Z";
    private record Applicant(jakarta.servlet.http.Cookie sessionCookie,
                             jakarta.servlet.http.Cookie csrfCookie, String csrfToken) {
    }
}
