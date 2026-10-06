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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the OpenAPI account/session contract through Spring MVC and a migrated SQLite database. */
@SpringBootTest(classes = IdentityAccessApplication.class)
@AutoConfigureMockMvc
class ApplicantAccountAccessHttpIntegrationTest {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final Path DATABASE_PATH = databasePath();
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger(10);

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
    void registrationContractValidatesCreatesAnApplicantAndDoesNotIssueASession() throws Exception {
        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials("applicant@example.com", "Secure!1"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/applicant-accounts/[0-9a-f-]{36}")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM applicant_accounts WHERE normalized_email = ?", String.class, "applicant@example.com"))
                .isEqualTo("APPLICANT");

        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials("applicant@example.com", "Secure!1"))))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"))
                .andExpect(jsonPath("$.errors[0].code").value("email-already-registered"));

        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials("not-an-email", "plain"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].source.pointer").value("/data/attributes/email"))
                .andExpect(jsonPath("$.errors[1].source.pointer").value("/data/attributes/password"));
    }

    @Test
    void signInIsNonEnumeratingAndSuccessfulAuthenticationIssuesSecureOpaqueCookie() throws Exception {
        register("known@example.com", "Secure!1");

        MvcResult unknownEmail = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("unknown@example.com", "Secure!1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].source").doesNotExist())
                .andReturn();
        MvcResult wrongPassword = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("known@example.com", "Wrong!1"))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());

        MvcResult successfulSignIn = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("known@example.com", "Secure!1"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/applicant-sessions/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data.type").value("applicant-sessions"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.attributes.href").value("/applications/current"))
                .andExpect(header().exists("X-CSRF-TOKEN"))
                .andReturn();

        HttpCookie sessionCookie = responseCookie(successfulSignIn, "__Host-KYCSESSION");
        assertThat(sessionCookie.getValue()).doesNotContain("known@example.com", "Secure!1");
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getSecure()).isTrue();
        assertThat(sessionCookie.getPath()).isEqualTo("/");
        assertThat(successfulSignIn.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.contains("SameSite=Strict"));
    }

    @Test
    void signOutRequiresCsrfForASessionThenRevokesAndClearsTheCookie() throws Exception {
        register("signout@example.com", "Secure!1");
        MvcResult signedIn = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("signout@example.com", "Secure!1"))))
                .andExpect(status().isCreated())
                .andReturn();
        HttpCookie sessionCookie = responseCookie(signedIn, "__Host-KYCSESSION");
        HttpCookie csrfCookie = responseCookie(signedIn, "XSRF-TOKEN");
        String csrfToken = signedIn.getResponse().getHeader("X-CSRF-TOKEN");

        mockMvc.perform(delete("/api/v1/applicant-sessions/current")
                        .cookie(toServletCookie(sessionCookie)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/applicant-sessions/current")
                        .cookie(toServletCookie(sessionCookie), toServletCookie(csrfCookie))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("__Host-KYCSESSION", 0));

        mockMvc.perform(delete("/api/v1/applicant-sessions/current")
                        .cookie(toServletCookie(sessionCookie), toServletCookie(csrfCookie))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSessionCookieHasTheOpenApiUnauthenticatedOutcome() throws Exception {
        mockMvc.perform(delete("/api/v1/applicant-sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("authentication-required"));
    }

    @Test
    void rejectsUnsupportedAndMalformedRepresentationsWithSafeJsonApiErrors() throws Exception {
        mockMvc.perform(post("/api/v1/applicant-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"))
                .andExpect(jsonPath("$.errors[0].code").value("unsupported-media-type"));

        mockMvc.perform(post("/api/v1/applicant-accounts")
                        .contentType("application/vnd.api+json;charset=UTF-8")
                        .content(credentials("parameter@example.com", "Secure!1")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json"))
                .andExpect(jsonPath("$.errors[0].code").value("unsupported-media-type"))
                .andExpect(jsonPath("$.errors[0].source").doesNotExist());

        mockMvc.perform(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("malformed-document"))
                .andExpect(jsonPath("$.errors[0].detail").value(
                        "The request body is not a valid JSON:API document."));
    }

    @Test
    void currentJourneyAndStartContractUseTheAuthenticatedApplicantAndCsrfBoundary() throws Exception {
        register("journey-owner@example.com", "Secure!1");
        MvcResult ownerSignIn = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("journey-owner@example.com", "Secure!1"))))
                .andExpect(status().isCreated())
                .andReturn();
        HttpCookie ownerSession = responseCookie(ownerSignIn, "__Host-KYCSESSION");
        HttpCookie ownerCsrfCookie = responseCookie(ownerSignIn, "XSRF-TOKEN");
        String ownerCsrfToken = ownerSignIn.getResponse().getHeader("X-CSRF-TOKEN");

        mockMvc.perform(get("/api/v1/applicant-applications/current"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/applicant-applications/current").cookie(toServletCookie(ownerSession)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/applicant-applications").cookie(toServletCookie(ownerSession)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/applicant-applications")
                        .cookie(toServletCookie(ownerSession), toServletCookie(ownerCsrfCookie))
                        .header("X-XSRF-TOKEN", ownerCsrfToken))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.data.type").value("applicant-applications"))
                .andExpect(jsonPath("$.data.attributes.status").value("draft"))
                .andExpect(jsonPath("$.data.attributes.currentStep").value("personal-details"))
                .andExpect(jsonPath("$.data.attributes.progress[0].state").value("current"))
                .andExpect(jsonPath("$.data.attributes.progress[1].state").value("not-started"))
                .andReturn();
        String applicationId = jsonString(created, "id");

        mockMvc.perform(post("/api/v1/applicant-applications")
                        .cookie(toServletCookie(ownerSession), toServletCookie(ownerCsrfCookie))
                        .header("X-XSRF-TOKEN", ownerCsrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(applicationId));
        mockMvc.perform(get("/api/v1/applicant-applications/current").cookie(toServletCookie(ownerSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(applicationId))
                .andExpect(jsonPath("$.data.attributes.href")
                        .value("/applications/" + applicationId + "/personal-details"));

        register("another-applicant@example.com", "Secure!1");
        MvcResult otherSignIn = mockMvc.perform(withClient(post("/api/v1/applicant-sessions")
                        .contentType(JSON_API)
                        .content(sessionCredentials("another-applicant@example.com", "Secure!1"))))
                .andExpect(status().isCreated())
                .andReturn();
        HttpCookie otherSession = responseCookie(otherSignIn, "__Host-KYCSESSION");
        mockMvc.perform(get("/api/v1/applicant-applications/current").cookie(toServletCookie(otherSession)))
                .andExpect(status().isNoContent());
    }

    @Test
    void registrationAndAuthenticationRateLimitsReturnRetryAfterWithoutCredentialDetails() throws Exception {
        String invalidRegistration = credentials("invalid", "plain");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(withClient(post("/api/v1/applicant-accounts"), "198.51.100.250")
                            .contentType(JSON_API)
                            .content(invalidRegistration))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(withClient(post("/api/v1/applicant-accounts"), "198.51.100.250")
                        .contentType(JSON_API)
                        .content(invalidRegistration))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].code").value("rate-limited"));

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(withClient(post("/api/v1/applicant-sessions"), "198.51.100.251")
                            .contentType(JSON_API)
                            .content(sessionCredentials("not-known@example.com", "Secure!1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(withClient(post("/api/v1/applicant-sessions"), "198.51.100.251")
                        .contentType(JSON_API)
                        .content(sessionCredentials("not-known@example.com", "Secure!1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].detail").value("Too many attempts. Try again later."));
    }

    private void register(final String email, final String password) throws Exception {
        mockMvc.perform(withClient(post("/api/v1/applicant-accounts")
                        .contentType(JSON_API)
                        .content(credentials(email, password))))
                .andExpect(status().isCreated());
    }

    private static String credentials(final String email, final String password) {
        return "{\"data\":{\"type\":\"applicant-accounts\",\"attributes\":{\"email\":\""
                + email + "\",\"password\":\"" + password + "\"}}}";
    }

    private static String sessionCredentials(final String email, final String password) {
        return "{\"data\":{\"type\":\"applicant-sessions\",\"attributes\":{\"email\":\""
                + email + "\",\"password\":\"" + password + "\"}}}";
    }

    private static MockHttpServletRequestBuilder withClient(final MockHttpServletRequestBuilder request) {
        return withClient(request, "198.51.100." + CLIENT_SEQUENCE.incrementAndGet());
    }

    private static MockHttpServletRequestBuilder withClient(
            final MockHttpServletRequestBuilder request, final String remoteAddress) {
        return request.with(mockRequest -> {
            mockRequest.setRemoteAddr(remoteAddress);
            return mockRequest;
        });
    }

    private static HttpCookie responseCookie(final MvcResult response, final String name) {
        return response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(HttpCookie::parse)
                .flatMap(List::stream)
                .filter(cookie -> name.equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected response cookie " + name));
    }

    private static String jsonString(final MvcResult response, final String field) throws Exception {
        String marker = "\"" + field + "\":\"";
        String body = response.getResponse().getContentAsString();
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("Expected JSON field " + field);
        }
        int valueStart = start + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new AssertionError("Expected a JSON string for " + field);
        }
        return body.substring(valueStart, valueEnd);
    }

    private static jakarta.servlet.http.Cookie toServletCookie(final HttpCookie cookie) {
        return new jakarta.servlet.http.Cookie(cookie.getName(), cookie.getValue());
    }

    private static Path databasePath() {
        try {
            Path path = Files.createTempFile("identity-access-http-contract-", ".db");
            Files.deleteIfExists(path);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare isolated SQLite test database", exception);
        }
    }
}
