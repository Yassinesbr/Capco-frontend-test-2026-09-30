package com.kyc.identityaccess.adapter.web;

import com.kyc.identityaccess.application.ApplicantAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Returns a body-free denial so internal resources are never disclosed to an Applicant. */
@RestControllerAdvice(assignableTypes = InternalAreaAccessController.class)
public class InternalAreaAccessExceptionHandler {

    /** Maps all internal-area authorization failures to a deliberately content-free denial. */
    @ExceptionHandler(ApplicantAccessDeniedException.class)
    ResponseEntity<JsonApiErrorDocument> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("403", "access-denied", "Access denied",
                        "You do not have permission to access this resource."));
    }
}
