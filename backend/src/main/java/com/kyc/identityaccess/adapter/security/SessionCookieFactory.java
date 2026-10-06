package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.domain.SessionId;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** Creates production-safe cookies that carry only opaque session identifiers. */
public final class SessionCookieFactory {

    /** Browser cookie name reserved for the opaque Applicant session. */
    public static final String SESSION_COOKIE_NAME = "__Host-KYCSESSION";

    private final SessionSecurityProperties properties;

    /** Creates a cookie factory from validated deployment settings. */
    public SessionCookieFactory(final SessionSecurityProperties properties) {
        this.properties = properties;
    }

    /** Creates the cookie issued after authenticated-session rotation. */
    public ResponseCookie issue(final SessionId sessionId) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, sessionId.value())
                .httpOnly(true)
                .secure(true)
                .sameSite(properties.sameSite())
                .path("/")
                .maxAge(properties.idleTimeout())
                .build();
    }

    /** Creates the cookie used to clear an ended session. */
    public ResponseCookie clear() {
        return ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(properties.sameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
