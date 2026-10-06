package com.kyc.identityaccess.adapter.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kyc.identityaccess.application.port.out.ApplicantSessionRepository;
import com.kyc.identityaccess.bootstrap.IdentityAccessApplication;
import com.kyc.identityaccess.domain.SessionId;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises privacy and expiry evidence at the HTTP, persistence, and logging boundaries. */
@SpringBootTest(classes = IdentityAccessApplication.class)
@AutoConfigureMockMvc
class OperationalSafetyIntegrationTest {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    private static final Path DATABASE_PATH = databasePath();
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger(70);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicantSessionRepository sessionRepository;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @DynamicPropertySource
    static void sqliteProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_PATH);
    }

    @BeforeEach
    void prepareIsolatedDatabaseAndCaptureLogs() {
        jdbcTemplate.update("DELETE FROM identity_access_audit_events");
        jdbcTemplate.update("DELETE FROM applicant_sessions");
        jdbcTemplate.update("DELETE FROM applicant_accounts");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturingLogs() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void failedAuthenticationPersistsOnlyAPrivacySafeFactAndLeaksNoCredentials() throws Exception {
        String email = "audit-observation@example.com";
        String password = "Observed!42";

        MvcResult response = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(credentials("applicant-sessions", email, password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("invalid-credentials"))
                .andReturn();

        assertThat(response.getResponse().getContentAsString()).doesNotContain(email, password);
        assertThat(new ApplicantAccountAccessController.CredentialsAttributes(email, password).toString())
                .doesNotContain(password)
                .contains("[REDACTED]");
        assertThat(capturedLogMessages()).doesNotContain(email, password);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT event_type, outcome, minimized_subject_reference "
                        + "FROM identity_access_audit_events"))
                .containsEntry("event_type", "AUTHENTICATION_FAILED")
                .containsEntry("outcome", "failure")
                .containsEntry("minimized_subject_reference", "credential-attempt");
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_type, outcome, correlation_id, minimized_subject_reference "
                        + "FROM identity_access_audit_events"))
                .allSatisfy(auditFact -> assertThat(auditFact.values().toString()).doesNotContain(email, password));
    }

    @Test
    void expiredSessionIsRemovedDuringLookupAndItsRawCookieValueNeverReachesPersistence() throws Exception {
        String email = "expiry-observation@example.com";
        register(email, "Secure!1");
        HttpCookie sessionCookie = signIn(email, "Secure!1");
        String opaqueSessionId = sessionCookie.getValue();

        assertThat(capturedLogMessages()).doesNotContain(email, "Secure!1", opaqueSessionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT session_id_hash FROM applicant_sessions", String.class)).doesNotContain(opaqueSessionId);
        jdbcTemplate.update(
                "UPDATE applicant_sessions SET idle_expires_at = ?", Instant.now().minusSeconds(1).toString());

        assertThat(sessionRepository.findActiveById(new SessionId(opaqueSessionId), Instant.now())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applicant_sessions", Integer.class)).isZero();
    }

    private void register(final String email, final String password) throws Exception {
        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials("applicant-accounts", email, password))))
                .andExpect(status().isCreated());
    }

    private HttpCookie signIn(final String email, final String password) throws Exception {
        MvcResult response = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(credentials("applicant-sessions", email, password))))
                .andExpect(status().isCreated())
                .andReturn();
        return response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(HttpCookie::parse)
                .flatMap(List::stream)
                .filter(cookie -> "__Host-KYCSESSION".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected an opaque Applicant session cookie"));
    }

    private String capturedLogMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private static String credentials(final String type, final String email, final String password) {
        return "{\"data\":{\"type\":\"" + type + "\",\"attributes\":{\"email\":\""
                + email + "\",\"password\":\"" + password + "\"}}}";
    }

    private static MockHttpServletRequestBuilder withClient(final MockHttpServletRequestBuilder request) {
        return request.with(mockRequest -> {
            mockRequest.setRemoteAddr("198.51.100." + CLIENT_SEQUENCE.incrementAndGet());
            return mockRequest;
        });
    }

    private static Path databasePath() {
        try {
            Path path = Files.createTempFile("identity-access-operational-safety-", ".db");
            Files.deleteIfExists(path);
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare isolated SQLite test database", exception);
        }
    }
}
