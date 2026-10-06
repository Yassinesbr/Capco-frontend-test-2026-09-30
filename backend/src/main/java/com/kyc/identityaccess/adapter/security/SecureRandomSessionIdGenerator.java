package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.application.port.out.SessionIdGenerator;
import com.kyc.identityaccess.domain.SessionId;
import java.security.SecureRandom;
import java.util.Base64;

/** CSPRNG-backed generator for opaque 256-bit session identifiers. */
public final class SecureRandomSessionIdGenerator implements SessionIdGenerator {

    private static final int SESSION_BYTES = 32;

    private final SecureRandom secureRandom;

    /** Creates a generator with the platform CSPRNG. */
    public SecureRandomSessionIdGenerator() {
        this(new SecureRandom());
    }

    /** Creates a generator for controlled tests. */
    SecureRandomSessionIdGenerator(final SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /** {@inheritDoc} */
    @Override
    public SessionId generate() {
        byte[] bytes = new byte[SESSION_BYTES];
        secureRandom.nextBytes(bytes);
        return new SessionId(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
