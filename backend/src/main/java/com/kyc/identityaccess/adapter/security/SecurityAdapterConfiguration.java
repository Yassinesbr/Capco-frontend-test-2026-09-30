package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.PasswordHashingPort;
import com.kyc.identityaccess.application.port.out.SessionIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Composition of security adapters; business policies remain framework-independent. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionSecurityProperties.class)
public class SecurityAdapterConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAdapterConfiguration.class);

    /** Provides OWASP-baseline password hashing. */
    @Bean
    PasswordHashingPort passwordHashingPort() {
        return new SpringArgon2PasswordHashingAdapter();
    }

    /** Provides opaque CSPRNG session identifiers. */
    @Bean
    SessionIdGenerator sessionIdGenerator() {
        return new SecureRandomSessionIdGenerator();
    }

    /** Provides the clock used by time-bounded security policies. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Provides conservative in-memory throttling until durable adapter work is complete. */
    @Bean
    AuthenticationRateLimitPort authenticationRateLimitPort(final Clock clock) {
        return new InMemoryAuthenticationRateLimiter(clock, 5, Duration.ofMinutes(15));
    }

    /** Creates opaque secure cookies using validated deployment settings. */
    @Bean
    SessionCookieFactory sessionCookieFactory(final SessionSecurityProperties properties) {
        return new SessionCookieFactory(properties);
    }

    /**
     * Exposes only the approved account/session routes. CSRF is required for sign-out whenever a
     * browser presents the authenticated session cookie; public credential submissions have no
     * ambient authority to protect.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity httpSecurity, final CsrfTokenRepository csrfTokenRepository) throws Exception {
        RequestMatcher authenticatedSignOut = request -> HttpMethod.DELETE.matches(request.getMethod())
                && "/api/v1/applicant-sessions/current".equals(request.getRequestURI())
                && hasApplicantSessionCookie(request.getCookies());
        RequestMatcher authenticatedApplicationStart = request -> HttpMethod.POST.matches(request.getMethod())
                && "/api/v1/applicant-applications".equals(request.getRequestURI())
                && hasApplicantSessionCookie(request.getCookies());
        RequestMatcher authenticatedFormWrite = request ->
                (HttpMethod.PATCH.matches(request.getMethod()) || HttpMethod.POST.matches(request.getMethod()))
                        && request.getRequestURI().startsWith("/api/v1/applicant-applications/")
                        && hasApplicantSessionCookie(request.getCookies());
        return httpSecurity
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, exception) -> {
                    String reason = exception instanceof MissingCsrfTokenException
                            ? "csrf_missing"
                            : exception instanceof InvalidCsrfTokenException ? "csrf_invalid" : "authorization_denied";
                    String requestId = requestId(request.getHeader("X-Request-ID"));
                    LOGGER.warn("Applicant request denied: requestId={} method={} reason={} exceptionType={}",
                            requestId, request.getMethod(), reason, exception.getClass().getSimpleName());
                    response.setHeader("X-Request-ID", requestId);
                    response.setStatus(403);
                    response.setContentType("application/vnd.api+json");
                    response.getOutputStream().write(("{\"errors\":[{\"status\":\"403\","
                            + "\"code\":\"access-denied\",\"title\":\"Access denied\","
                            + "\"detail\":\"You do not have permission to access this resource.\"}]}")
                            .getBytes(StandardCharsets.UTF_8));
                }))
                .csrf(csrf -> csrf
                        .requireCsrfProtectionMatcher(
                                new OrRequestMatcher(authenticatedSignOut, authenticatedApplicationStart,
                                        authenticatedFormWrite))
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/applicant-accounts", "/api/v1/applicant-sessions")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applicant-applications/current")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/applicant-applications")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/applicant-applications/*",
                                "/api/v1/applicant-applications/*/form",
                                "/api/v1/applicant-applications/*/review")
                        .permitAll()
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/applicant-applications/*",
                                "/api/v1/applicant-applications/*/form")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/applicant-applications/*/document-evidence")
                        .permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/applicant-sessions/current")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/reviewer",
                                "/administrator",
                                "/api/v1/internal/reviewer",
                                "/api/v1/internal/admin",
                                "/api/v1/internal/administrator",
                                "/api/v1/reviewer-applications")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .build();
    }

    /** Creates a JavaScript-readable double-submit token that is not an authentication secret. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Strict"));
        return repository;
    }

    private static String requestId(final String value) {
        if (value == null) {
            return "unavailable";
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    private static boolean hasApplicantSessionCookie(final Cookie[] cookies) {
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (SessionCookieFactory.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
