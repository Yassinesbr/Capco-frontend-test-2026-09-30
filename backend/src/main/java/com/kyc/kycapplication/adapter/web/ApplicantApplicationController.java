package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.AuthorizedApplicant;
import com.kyc.identityaccess.application.ProtectedWorkArea;
import com.kyc.identityaccess.domain.SessionId;
import com.kyc.kycapplication.application.FindCurrentApplication;
import com.kyc.kycapplication.application.StartApplication;
import com.kyc.kycapplication.bootstrap.ApplicationJourneyMapper;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.KycApplication;
import com.kyc.kycapplication.domain.StepProgress;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP contract for an Applicant's minimized current KYC journey. */
@RestController
@RequestMapping("/api/v1/applicant-applications")
public class ApplicantApplicationController {

    private static final String JSON_API = "application/vnd.api+json";

    private final AuthorizeApplicantAccess authorizeApplicantAccess;
    private final FindCurrentApplication findCurrentApplication;
    private final StartApplication startApplication;

    /** Creates the HTTP adapter from the established session boundary and KYC use cases. */
    public ApplicantApplicationController(
            final AuthorizeApplicantAccess authorizeApplicantAccess,
            final FindCurrentApplication findCurrentApplication,
            final StartApplication startApplication) {
        this.authorizeApplicantAccess = authorizeApplicantAccess;
        this.findCurrentApplication = findCurrentApplication;
        this.startApplication = startApplication;
    }

    /** Returns no content when the authenticated Applicant has not started a draft. */
    @GetMapping(value = "/current", produces = JSON_API)
    public ResponseEntity<ApplicationJourneyDocument> current(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionCookie) {
        ApplicantId applicantId = applicantId(sessionCookie);
        return findCurrentApplication.findFor(applicantId)
                .map(application -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(JSON_API))
                        .body(ApplicationJourneyDocument.from(application)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Starts a draft once, or returns the Applicant's existing active draft. */
    @PostMapping(produces = JSON_API)
    public ResponseEntity<ApplicationJourneyDocument> start(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionCookie) {
        StartApplication.StartResult result = startApplication.start(applicantId(sessionCookie));
        var response = ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .contentType(MediaType.parseMediaType(JSON_API));
        if (result.created()) {
            response.header(HttpHeaders.LOCATION,
                    "/api/v1/applicant-applications/" + result.application().id().value());
        }
        return response.body(ApplicationJourneyDocument.from(result.application()));
    }

    private ApplicantId applicantId(final String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new ApplicantAuthenticationRequiredException();
        }
        AuthorizedApplicant applicant = authorizeApplicantAccess.authorize(
                new SessionId(sessionCookie), ProtectedWorkArea.APPLICANT);
        return new ApplicantId(applicant.accountId());
    }

    /** Minimized response model matching the KYC Application journey OpenAPI schema. */
    public record ApplicationJourneyDocument(ApplicationJourneyResource data) {

        private static ApplicationJourneyDocument from(final KycApplication application) {
            return new ApplicationJourneyDocument(ApplicationJourneyResource.from(application));
        }
    }

    /** JSON:API application resource. */
    public record ApplicationJourneyResource(
            String type,
            String id,
            ApplicationJourneyAttributes attributes) {

        private static ApplicationJourneyResource from(final KycApplication application) {
            return new ApplicationJourneyResource(
                    "applicant-applications",
                    application.id().value().toString(),
                    ApplicationJourneyAttributes.from(application));
        }
    }

    /** Minimized KYC journey attributes. */
    public record ApplicationJourneyAttributes(
            String status,
            String currentStep,
            List<StepProgressResponse> progress,
            String href) {

        private static ApplicationJourneyAttributes from(final KycApplication application) {
            return new ApplicationJourneyAttributes(
                    kebabCase(application.status().name()),
                    kebabCase(application.currentStep().name()),
                    application.progress().stream().map(StepProgressResponse::from).toList(),
                    ApplicationJourneyMapper.href(application));
        }
    }

    /** Minimized progress item matching the KYC Application journey OpenAPI schema. */
    public record StepProgressResponse(String step, String state) {

        private static StepProgressResponse from(final StepProgress progress) {
            return new StepProgressResponse(kebabCase(progress.step().name()), kebabCase(progress.state().name()));
        }
    }

    private static String kebabCase(final String enumName) {
        return enumName.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
