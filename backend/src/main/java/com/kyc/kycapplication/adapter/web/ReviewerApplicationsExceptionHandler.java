package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.application.ApplicantAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Body-safe denials for reviewer overview requests. */
@RestControllerAdvice(assignableTypes = ReviewerApplicationsController.class)
public class ReviewerApplicationsExceptionHandler {
    @ExceptionHandler(ApplicantAccessDeniedException.class)
    ResponseEntity<JsonApiErrorDocument> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                .body(JsonApiErrorDocument.one("403", "access-denied", "Access denied",
                        "You do not have permission to access this resource."));
    }
}
