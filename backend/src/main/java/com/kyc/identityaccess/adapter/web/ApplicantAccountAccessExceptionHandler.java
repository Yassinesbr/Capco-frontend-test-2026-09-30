package com.kyc.identityaccess.adapter.web;

import com.kyc.identityaccess.application.AuthenticationFailedException;
import com.kyc.identityaccess.application.AuthenticationRateLimitedException;
import com.kyc.identityaccess.application.EmailAlreadyRegisteredException;
import com.kyc.identityaccess.application.NoActiveApplicantSessionException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps public HTTP failures deliberate, validated, and free of credential-enumeration detail. */
@RestControllerAdvice(assignableTypes = ApplicantAccountAccessController.class)
public class ApplicantAccountAccessExceptionHandler {

    /** Maps duplicate normalized email values to the registration contract conflict response. */
    @ExceptionHandler({EmailAlreadyRegisteredException.class, DataIntegrityViolationException.class})
    ResponseEntity<JsonApiErrorDocument> emailAlreadyRegistered() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("409", "email-already-registered", "Account conflict",
                        "An account already exists for this email address."));
    }

    /** Uses one indistinguishable status, code, and body for every invalid sign-in credential. */
    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<JsonApiErrorDocument> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("401", "invalid-credentials", "Authentication failed",
                        "Invalid email or password"));
    }

    /** Makes throttling explicit without exposing an account-specific reason. */
    @ExceptionHandler(AuthenticationRateLimitedException.class)
    ResponseEntity<JsonApiErrorDocument> authenticationRateLimited(
            final AuthenticationRateLimitedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(exception.retryAfter())))
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("429", "rate-limited", "Too many requests",
                        "Too many attempts. Try again later."));
    }

    /** Reports absent, expired, or revoked session cookies as unauthenticated. */
    @ExceptionHandler(NoActiveApplicantSessionException.class)
    ResponseEntity<JsonApiErrorDocument> noActiveSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("401", "authentication-required", "Authentication required",
                        "Sign in to continue."));
    }

    private static long retryAfterSeconds(final Instant retryAfter) {
        return retryAfter == null ? 1 : Math.max(1, Duration.between(Instant.now(), retryAfter).toSeconds());
    }
}
