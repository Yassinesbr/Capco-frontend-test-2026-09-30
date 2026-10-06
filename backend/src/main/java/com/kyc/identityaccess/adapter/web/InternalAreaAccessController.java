package com.kyc.identityaccess.adapter.web;

import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.ApplicantAccessDeniedException;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.ProtectedWorkArea;
import com.kyc.identityaccess.domain.SessionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for internal work areas. It relies on the server-side session role, never client
 * route state or role claims.
 */
@RestController
public class InternalAreaAccessController {

    private final AuthorizeApplicantAccess authorizeApplicantAccess;

    /** Creates the internal-area HTTP boundary from the authorization use case. */
    public InternalAreaAccessController(final AuthorizeApplicantAccess authorizeApplicantAccess) {
        this.authorizeApplicantAccess = authorizeApplicantAccess;
    }

    /** Denies an Applicant who attempts to navigate directly to the reviewer work area. */
    @GetMapping("/reviewer")
    public ResponseEntity<Void> reviewerRoute(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionCookie) {
        authorize(sessionCookie, ProtectedWorkArea.REVIEWER);
        return ResponseEntity.noContent().build();
    }

    /** Denies an Applicant who attempts to navigate directly to the administrator work area. */
    @GetMapping("/administrator")
    public ResponseEntity<Void> administratorRoute(
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionCookie) {
        authorize(sessionCookie, ProtectedWorkArea.ADMINISTRATOR);
        return ResponseEntity.noContent().build();
    }

    /** Denies an Applicant who calls the reviewer or administrator API directly. */
    @GetMapping("/api/v1/internal/{area}")
    public ResponseEntity<Void> internalApi(
            @PathVariable final String area,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false)
                    final String sessionCookie) {
        authorize(sessionCookie, internalWorkArea(area));
        return ResponseEntity.noContent().build();
    }

    private void authorize(final String sessionCookie, final ProtectedWorkArea workArea) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new ApplicantAccessDeniedException();
        }
        authorizeApplicantAccess.authorize(new SessionId(sessionCookie), workArea);
    }

    private static ProtectedWorkArea internalWorkArea(final String area) {
        return switch (area) {
            case "reviewer" -> ProtectedWorkArea.REVIEWER;
            case "admin", "administrator" -> ProtectedWorkArea.ADMINISTRATOR;
            default -> throw new ApplicantAccessDeniedException();
        };
    }
}
