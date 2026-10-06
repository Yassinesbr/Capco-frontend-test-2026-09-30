package com.kyc.identityaccess.adapter.web;

import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.AuthenticationRateLimitedException;
import com.kyc.identityaccess.application.RegisterApplicant;
import com.kyc.identityaccess.application.SignInApplicant;
import com.kyc.identityaccess.application.SignOutApplicant;
import com.kyc.identityaccess.application.SignedInApplicant;
import com.kyc.identityaccess.application.port.out.AuthenticationRateLimitPort;
import com.kyc.identityaccess.application.port.out.PrivacySafeRateLimitKey;
import com.kyc.identityaccess.domain.SessionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the approved Applicant account and session OpenAPI contract. */
@RestController
@RequestMapping("/api/v1")
public class ApplicantAccountAccessController {

    private static final String JSON_API = "application/vnd.api+json";

    private final RegisterApplicant registerApplicant;
    private final SignInApplicant signInApplicant;
    private final SignOutApplicant signOutApplicant;
    private final AuthenticationRateLimitPort rateLimitPort;
    private final SessionCookieFactory sessionCookieFactory;
    private final CsrfTokenRepository csrfTokenRepository;

    /** Creates the HTTP adapter from use cases and HTTP-specific security adapters. */
    public ApplicantAccountAccessController(
            final RegisterApplicant registerApplicant,
            final SignInApplicant signInApplicant,
            final SignOutApplicant signOutApplicant,
            final AuthenticationRateLimitPort rateLimitPort,
            final SessionCookieFactory sessionCookieFactory,
            final CsrfTokenRepository csrfTokenRepository) {
        this.registerApplicant = registerApplicant;
        this.signInApplicant = signInApplicant;
        this.signOutApplicant = signOutApplicant;
        this.rateLimitPort = rateLimitPort;
        this.sessionCookieFactory = sessionCookieFactory;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /** Registers an Applicant without establishing an authenticated browser session. */
    @PostMapping(value = "/applicant-accounts", consumes = JSON_API, produces = JSON_API)
    public ResponseEntity<?> register(
            @RequestBody final CredentialsDocument document, final HttpServletRequest request) {
        ResponseEntity<JsonApiErrorDocument> unsupportedParameters = rejectMediaTypeParameters(request);
        if (unsupportedParameters != null) {
            return unsupportedParameters;
        }
        PrivacySafeRateLimitKey rateLimitKey = rateLimitKey("registration", request);
        ResponseEntity<JsonApiErrorDocument> throttled = rateLimitedResponse(rateLimitPort.check(rateLimitKey));
        if (throttled != null) {
            return throttled;
        }
        List<JsonApiErrorDocument.JsonApiError> validationErrors = validate(document, "applicant-accounts");
        if (!validationErrors.isEmpty()) {
            rateLimitPort.recordFailure(rateLimitKey);
            return ResponseEntity.badRequest().contentType(jsonApiMediaType()).body(new JsonApiErrorDocument(validationErrors));
        }
        CredentialsAttributes credentials = document.data().attributes();
        char[] password = credentials.password().toCharArray();
        try {
            var registered = registerApplicant.register(credentials.email(), password);
            rateLimitPort.clear(rateLimitKey);
            return ResponseEntity.created(java.net.URI.create("/api/v1/applicant-accounts/" + registered.id()))
                    .build();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Authenticates with a uniform public failure and an opaque rotated session on success. */
    @PostMapping(value = "/applicant-sessions", consumes = JSON_API, produces = JSON_API)
    public ResponseEntity<?> signIn(
            @RequestBody final CredentialsDocument document,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionToRotate,
            final HttpServletRequest request) {
        ResponseEntity<JsonApiErrorDocument> unsupportedParameters = rejectMediaTypeParameters(request);
        if (unsupportedParameters != null) {
            return unsupportedParameters;
        }
        List<JsonApiErrorDocument.JsonApiError> validationErrors = validate(document, "applicant-sessions");
        if (!validationErrors.isEmpty()) {
            return ResponseEntity.badRequest().contentType(jsonApiMediaType()).body(new JsonApiErrorDocument(validationErrors));
        }
        CredentialsAttributes credentials = document.data().attributes();
        char[] password = credentials.password().toCharArray();
        try {
            SignedInApplicant signedInApplicant = signInApplicant.signIn(
                    credentials.email(),
                    password,
                    rateLimitKey("sign-in", request),
                    sessionId(sessionToRotate));
            CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
            String publicId = publicSessionId(signedInApplicant.sessionId());
            return ResponseEntity.created(java.net.URI.create("/api/v1/applicant-sessions/" + publicId))
                    .contentType(jsonApiMediaType())
                    .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.issue(signedInApplicant.sessionId()).toString())
                    .header(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken).toString())
                    .header("X-CSRF-TOKEN", csrfToken.getToken())
                    .body(new ApplicantSessionDocument(new ApplicantSessionResource(
                            "applicant-sessions", publicId,
                            new ApplicantSessionAttributes(signedInApplicant.journey().href()))));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Revokes the supplied opaque session and clears the browser's session cookie. */
    @DeleteMapping("/applicant-sessions/current")
    public ResponseEntity<?> signOut(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String currentSessionId) {
        if (currentSessionId == null || currentSessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(jsonApiMediaType())
                    .body(JsonApiErrorDocument.one("401", "authentication-required", "Authentication required",
                            "Sign in to continue."));
        }
        signOutApplicant.signOut(new SessionId(currentSessionId));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.clear().toString())
                .build();
    }

    private static List<JsonApiErrorDocument.JsonApiError> validate(
            final CredentialsDocument document, final String expectedType) {
        List<JsonApiErrorDocument.JsonApiError> errors = new ArrayList<>();
        CredentialsResource data = document == null ? null : document.data();
        CredentialsAttributes credentials = data == null ? null : data.attributes();
        if (data == null) {
            errors.add(fieldError("Request data is required.", "/data"));
            return errors;
        }
        if (!expectedType.equals(data.type())) {
            errors.add(fieldError("Resource type must be " + expectedType + ".", "/data/type"));
        }
        if (credentials == null || credentials.email() == null || credentials.email().isBlank()) {
            errors.add(fieldError("Enter an email address.", "/data/attributes/email"));
        } else if (!credentials.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            errors.add(fieldError("Enter a valid email address.", "/data/attributes/email"));
        }
        if (credentials == null || credentials.password() == null || credentials.password().isBlank()) {
            errors.add(fieldError("Enter a password.", "/data/attributes/password"));
        } else if (!passwordMeetsPolicy(credentials.password())) {
            errors.add(fieldError("Use at least 6 characters including an uppercase letter and special character.",
                    "/data/attributes/password"));
        }
        return errors;
    }

    private static JsonApiErrorDocument.JsonApiError fieldError(final String detail, final String pointer) {
        return new JsonApiErrorDocument.JsonApiError(
                "400", "validation-failed", "Invalid attribute", detail,
                new JsonApiErrorDocument.JsonApiErrorSource(pointer));
    }

    private static boolean passwordMeetsPolicy(final String password) {
        boolean hasUppercase = false;
        boolean hasSpecial = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            hasUppercase |= Character.isUpperCase(character);
            hasSpecial |= !Character.isLetterOrDigit(character);
        }
        return password.length() >= 6 && hasUppercase && hasSpecial;
    }

    private static PrivacySafeRateLimitKey rateLimitKey(final String operation, final HttpServletRequest request) {
        String networkIdentifier = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(networkIdentifier.getBytes(StandardCharsets.UTF_8));
            return new PrivacySafeRateLimitKey(operation + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static Optional<SessionId> sessionId(final String sessionToRotate) {
        return sessionToRotate == null || sessionToRotate.isBlank()
                ? Optional.empty()
                : Optional.of(new SessionId(sessionToRotate));
    }

    private static ResponseCookie csrfCookie(final CsrfToken csrfToken) {
        return ResponseCookie.from("XSRF-TOKEN", csrfToken.getToken())
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .build();
    }

    private static ResponseEntity<JsonApiErrorDocument> rateLimitedResponse(
            final AuthenticationRateLimitPort.RateLimitDecision decision) {
        if (decision.allowed()) {
            return null;
        }
        long retryAfter = retryAfterSeconds(decision.retryAfter());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                .contentType(jsonApiMediaType())
                .body(JsonApiErrorDocument.one("429", "rate-limited", "Too many requests",
                        "Too many attempts. Try again later."));
    }

    private static long retryAfterSeconds(final Instant retryAfter) {
        if (retryAfter == null) {
            return 1;
        }
        return Math.max(1, Duration.between(Instant.now(), retryAfter).toSeconds());
    }

    /** Incoming credentials are deliberately write-only and never returned by this adapter. */
    public record CredentialsDocument(CredentialsResource data) {
    }

    /** JSON:API resource object for credential-bearing create requests. */
    public record CredentialsResource(String type, CredentialsAttributes attributes) {
    }

    /** Incoming credentials are deliberately write-only and never returned by this adapter. */
    public record CredentialsAttributes(String email, String password) {

        /** Prevents framework debug logging from rendering submitted credential values. */
        @Override
        public String toString() {
            return "CredentialsAttributes[email=[REDACTED], password=[REDACTED]]";
        }
    }

    /** JSON:API session document. */
    public record ApplicantSessionDocument(ApplicantSessionResource data) {
    }

    /** Public session identity and its non-sensitive attributes. */
    public record ApplicantSessionResource(String type, String id, ApplicantSessionAttributes attributes) {
    }

    /** Session destination exposed to the first-party client. */
    public record ApplicantSessionAttributes(String href) {
    }

    private static MediaType jsonApiMediaType() {
        return MediaType.parseMediaType(JSON_API);
    }

    private static ResponseEntity<JsonApiErrorDocument> rejectMediaTypeParameters(
            final HttpServletRequest request) {
        MediaType contentType = MediaType.parseMediaType(request.getContentType());
        if (contentType.getParameters().isEmpty()) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(jsonApiMediaType())
                .body(JsonApiErrorDocument.one("415", "unsupported-media-type", "Unsupported media type",
                        "Use application/vnd.api+json without media type parameters."));
    }

    private static String publicSessionId(final SessionId sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.value().getBytes(StandardCharsets.UTF_8));
            ByteBuffer bytes = ByteBuffer.wrap(digest);
            long mostSignificant = bytes.getLong();
            long leastSignificant = bytes.getLong();
            mostSignificant = (mostSignificant & 0xffffffffffff0fffL) | 0x0000000000005000L;
            leastSignificant = (leastSignificant & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(mostSignificant, leastSignificant).toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
