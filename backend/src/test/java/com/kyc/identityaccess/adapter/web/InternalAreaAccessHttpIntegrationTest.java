package com.kyc.identityaccess.adapter.web;

import com.kyc.identityaccess.bootstrap.IdentityAccessApplication;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that browser routes and APIs reject Applicant sessions independently of client state. */
@SpringBootTest(classes = IdentityAccessApplication.class)
@AutoConfigureMockMvc
class InternalAreaAccessHttpIntegrationTest {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    private static final Path DATABASE_PATH = databasePath();
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger(40);

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
        jdbcTemplate.update("DELETE FROM identity_access_audit_events");
        jdbcTemplate.update("DELETE FROM applicant_sessions");
        jdbcTemplate.update("DELETE FROM applicant_accounts");
    }

    @Test
    void applicantSessionIsDeniedFromDirectReviewerRouteWithoutProtectedContent() throws Exception {
        HttpCookie sessionCookie = signedInApplicant("direct-route@example.com");

        MvcResult result = mockMvc.perform(get("/reviewer").cookie(toServletCookie(sessionCookie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("access-denied"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("direct-route@example.com");
        assertThat(denialEvents()).isEqualTo(1);
    }

    @Test
    void manipulatedClientRoleStateCannotReadAdministratorApiData() throws Exception {
        HttpCookie sessionCookie = signedInApplicant("client-state@example.com");

        MvcResult result = mockMvc.perform(get("/api/v1/internal/admin")
                        .cookie(toServletCookie(sessionCookie))
                        .header("X-Client-Role", "ADMINISTRATOR")
                        .param("role", "ADMINISTRATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("access-denied"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("client-state@example.com", sessionCookie.getValue(), "protected");
        assertThat(denialEvents()).isEqualTo(1);
    }

    private HttpCookie signedInApplicant(final String email) throws Exception {
        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials("applicant-accounts", email))))
                .andExpect(status().isCreated());
        MvcResult signedIn = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(credentials("applicant-sessions", email))))
                .andExpect(status().isCreated())
                .andReturn();
        return signedIn.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(HttpCookie::parse)
                .flatMap(List::stream)
                .filter(cookie -> "__Host-KYCSESSION".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected Applicant session cookie"));
    }

    private int denialEvents() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_access_audit_events WHERE event_type = 'AUTHORIZATION_DENIED'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private static String credentials(final String type, final String email) {
        return "{\"data\":{\"type\":\"" + type + "\",\"attributes\":{\"email\":\""
                + email + "\",\"password\":\"Secure!1\"}}}";
    }

    private static MockHttpServletRequestBuilder withClient(final MockHttpServletRequestBuilder request) {
        return request.with(mockRequest -> {
            mockRequest.setRemoteAddr("198.51.100." + CLIENT_SEQUENCE.incrementAndGet());
            return mockRequest;
        });
    }

    private static jakarta.servlet.http.Cookie toServletCookie(final HttpCookie cookie) {
        return new jakarta.servlet.http.Cookie(cookie.getName(), cookie.getValue());
    }

    private static Path databasePath() {
        try {
            Path path = Files.createTempFile("identity-access-internal-area-", ".db");
            Files.deleteIfExists(path);
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare isolated SQLite test database", exception);
        }
    }
}
