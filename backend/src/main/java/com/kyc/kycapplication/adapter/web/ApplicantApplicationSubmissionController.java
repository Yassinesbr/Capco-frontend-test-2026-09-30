package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.AuthorizedApplicant;
import com.kyc.identityaccess.application.ProtectedWorkArea;
import com.kyc.identityaccess.domain.SessionId;
import com.kyc.kycapplication.application.GetApplicantApplicationStatus;
import com.kyc.kycapplication.application.SubmitApplication;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStatus;
import com.kyc.kycapplication.domain.KycApplication;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped JSON:API adapter for application lifecycle status and deliberate submission. */
@RestController
@RequestMapping("/api/v1/applicant-applications/{applicationId}")
public class ApplicantApplicationSubmissionController {

    private static final String JSON_API = "application/vnd.api+json";
    private final AuthorizeApplicantAccess authorizeApplicantAccess;
    private final GetApplicantApplicationStatus getStatus;
    private final SubmitApplication submitApplication;

    public ApplicantApplicationSubmissionController(
            final AuthorizeApplicantAccess authorizeApplicantAccess,
            final GetApplicantApplicationStatus getStatus,
            final SubmitApplication submitApplication) {
        this.authorizeApplicantAccess = authorizeApplicantAccess;
        this.getStatus = getStatus;
        this.submitApplication = submitApplication;
    }

    @GetMapping(produces = JSON_API)
    public ResponseEntity<ApplicationDocument> status(
            @PathVariable final UUID applicationId,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie) {
        KycApplication application = getStatus.find(applicantId(sessionCookie), new ApplicationId(applicationId));
        return json(HttpStatus.OK, ApplicationDocument.from(application));
    }

    @PatchMapping(consumes = JSON_API, produces = JSON_API)
    public ResponseEntity<ApplicationDocument> submit(
            @PathVariable final UUID applicationId,
            @RequestBody final SubmissionDocument request,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie) {
        if (request == null || request.data() == null || request.data().attributes() == null
                || !"applicant-applications".equals(request.data().type())
                || !applicationId.toString().equals(request.data().id())
                || !"submitted".equals(request.data().attributes().status())
                || !Boolean.TRUE.equals(request.data().attributes().confirmed())
                || request.data().attributes().expectedFormVersion() == null
                || request.data().attributes().expectedFormVersion() < 0) {
            throw new IllegalArgumentException("submission confirmation is invalid");
        }
        SubmitApplication.SubmissionResult result = submitApplication.submit(
                applicantId(sessionCookie), new ApplicationId(applicationId),
                request.data().attributes().expectedFormVersion());
        return json(HttpStatus.OK, ApplicationDocument.from(result.application()));
    }

    private ApplicantId applicantId(final String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new ApplicantAuthenticationRequiredException();
        }
        AuthorizedApplicant applicant = authorizeApplicantAccess.authorize(
                new SessionId(sessionCookie), ProtectedWorkArea.APPLICANT);
        return new ApplicantId(applicant.accountId());
    }

    private static <T> ResponseEntity<T> json(final HttpStatus status, final T body) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JSON_API)).body(body);
    }

    public record SubmissionDocument(SubmissionResource data) {
    }

    public record SubmissionResource(String type, String id, SubmissionAttributes attributes) {
    }

    public record SubmissionAttributes(String status, Boolean confirmed, Integer expectedFormVersion) {
    }

    public record ApplicationDocument(ApplicationResource data) {
        static ApplicationDocument from(final KycApplication application) {
            ApplicationReceipt receipt = application.status() == ApplicationStatus.SUBMITTED
                    ? new ApplicationReceipt("Your application was received",
                            "The review team will review your application.") : null;
            return new ApplicationDocument(new ApplicationResource(
                    "applicant-applications", application.id().value().toString(),
                    new ApplicationAttributes(kebab(application.status().name()), application.submittedAt(), receipt)));
        }
    }

    public record ApplicationResource(String type, String id, ApplicationAttributes attributes) {
    }

    public record ApplicationAttributes(
            String status,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            java.time.Instant submittedAt,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            ApplicationReceipt receipt) {
    }

    public record ApplicationReceipt(String message, String nextStep) {
    }

    private static String kebab(final String enumName) {
        return enumName.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
