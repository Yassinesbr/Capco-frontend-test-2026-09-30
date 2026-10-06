package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.domain.SessionId;

/** Generates opaque session identifiers. */
public interface SessionIdGenerator {

    /** Generates an unpredictable session identifier. */
    SessionId generate();
}
