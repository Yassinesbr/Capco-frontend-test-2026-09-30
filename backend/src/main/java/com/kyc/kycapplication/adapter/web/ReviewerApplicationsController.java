package com.kyc.kycapplication.adapter.web;

import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.ApplicantAccessDeniedException;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.ProtectedWorkArea;
import com.kyc.identityaccess.domain.SessionId;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository.ReviewerApplicationPage;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository.ReviewerApplicationRow;
import com.kyc.kycapplication.application.ListReviewerApplications;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Protected, minimized reviewer workload read boundary. */
@RestController
public class ReviewerApplicationsController {
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private final AuthorizeApplicantAccess authorization;
    private final ListReviewerApplications listReviewerApplications;

    public ReviewerApplicationsController(final AuthorizeApplicantAccess authorization,
            final ListReviewerApplications listReviewerApplications) {
        this.authorization = authorization;
        this.listReviewerApplications = listReviewerApplications;
    }

    @GetMapping(value = "/api/v1/reviewer-applications", produces = "application/vnd.api+json")
    public ResponseEntity<?> list(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie,
            @RequestParam(value = "search", defaultValue = "") final String search,
            @RequestParam(value = "status", defaultValue = "all") final String status,
            @RequestParam(value = "page", defaultValue = "0") final int page,
            @RequestParam(value = "page-size", defaultValue = "10") final int pageSize) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(JSON_API)
                    .body(JsonApiErrorDocument.one("401", "authentication-required", "Authentication required",
                            "Sign in to continue."));
        }
        authorization.authorize(new SessionId(sessionCookie), ProtectedWorkArea.REVIEWER);
        try {
            ReviewerApplicationPage result = listReviewerApplications.list(search, status, page, pageSize);
            List<Resource> data = result.applications().stream().map(ReviewerApplicationsController::resource).toList();
            int totalPages = result.totalItems() == 0 ? 0 : (int) ((result.totalItems() + pageSize - 1) / pageSize);
            Summary summary = new Summary(result.pendingReview(), result.inProgress(), result.approvedThisMonth());
            Pagination pagination = new Pagination(page, pageSize, result.totalItems(), totalPages);
            return ResponseEntity.ok().contentType(JSON_API).body(new Document(data, new Meta(summary, pagination)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(JSON_API)
                    .body(JsonApiErrorDocument.one("400", "invalid-query", "Invalid query",
                            "Check search, status, page, and page size."));
        }
    }

    private static Resource resource(final ReviewerApplicationRow row) {
        String status = row.status().name().toLowerCase(Locale.ROOT).replace('_', '-');
        return new Resource("reviewer-applications", row.id(), new Attributes(row.applicantName(), row.submittedAt(), status));
    }

    public record Document(List<Resource> data, Meta meta) {}
    public record Meta(Summary summary, Pagination pagination) {}
    public record Summary(int pendingReview, int inProgress, int approvedThisMonth) {}
    public record Pagination(int page, int pageSize, long totalItems, int totalPages) {}
    public record Resource(String type, String id, Attributes attributes) {}
    public record Attributes(String applicantName, java.time.Instant submittedAt, String status) {}
}
