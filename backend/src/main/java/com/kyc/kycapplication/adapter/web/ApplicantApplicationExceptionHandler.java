package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.application.ApplicantAccessDeniedException;
import com.kyc.kycapplication.application.ApplicationNotFoundException;
import com.kyc.kycapplication.application.FormVersionConflictException;
import com.kyc.kycapplication.application.InvalidDocumentEvidenceException;
import com.kyc.kycapplication.domain.IncompleteApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Maps Applicant journey authorization failures without disclosing application information. */
@RestControllerAdvice(assignableTypes = {
    ApplicantApplicationController.class, ApplicantKycFormController.class, ApplicantApplicationSubmissionController.class
})
public class ApplicantApplicationExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicantApplicationExceptionHandler.class);

    /** Returns the OpenAPI unauthenticated outcome for a missing, expired, or revoked session. */
    @ExceptionHandler({ApplicantAuthenticationRequiredException.class, ApplicantAccessDeniedException.class})
    ResponseEntity<JsonApiErrorDocument> authenticationRequired() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.authenticationRequired());
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    ResponseEntity<JsonApiErrorDocument> notFound() {
        return response(HttpStatus.NOT_FOUND, JsonApiErrorDocument.notFound());
    }

    @ExceptionHandler(FormVersionConflictException.class)
    ResponseEntity<JsonApiErrorDocument> conflict() {
        return response(HttpStatus.CONFLICT, JsonApiErrorDocument.conflict());
    }

    @ExceptionHandler(InvalidDocumentEvidenceException.class)
    ResponseEntity<JsonApiErrorDocument> unprocessable(final InvalidDocumentEvidenceException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, JsonApiErrorDocument.unprocessable(exception.getMessage()));
    }

    @ExceptionHandler(IncompleteApplicationException.class)
    ResponseEntity<JsonApiErrorDocument> incomplete(final IncompleteApplicationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                JsonApiErrorDocument.incomplete(exception.missingFields().stream()
                        .map(com.kyc.kycapplication.domain.MissingRequiredField::field).toList()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<JsonApiErrorDocument> persistenceUnavailable(
            final DataAccessException exception, final HttpServletRequest request) {
        LOGGER.error("Applicant application request failed: requestId={} method={} outcome=persistence_error exceptionType={}",
                requestId(request), request.getMethod(), exception.getClass().getSimpleName(), exception);
        return response(HttpStatus.SERVICE_UNAVAILABLE, JsonApiErrorDocument.submissionUnavailable());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<JsonApiErrorDocument> unexpected(
            final Exception exception, final HttpServletRequest request) {
        LOGGER.error("Applicant application request failed: requestId={} method={} outcome=unexpected_error exceptionType={}",
                requestId(request), request.getMethod(), exception.getClass().getSimpleName(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, JsonApiErrorDocument.internalError());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<JsonApiErrorDocument> invalidRequest() {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                JsonApiErrorDocument.unprocessable("The submitted form could not be accepted."));
    }

    private static ResponseEntity<JsonApiErrorDocument> response(
            final HttpStatus status, final JsonApiErrorDocument document) {
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(document);
    }

    private static String requestId(final HttpServletRequest request) {
        String value = request.getHeader("X-Request-ID");
        if (value == null) {
            return "unavailable";
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }
}
