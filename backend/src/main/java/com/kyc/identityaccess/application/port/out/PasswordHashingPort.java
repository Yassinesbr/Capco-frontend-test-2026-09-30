package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.domain.Password;
import com.kyc.identityaccess.domain.PasswordVerifier;

/** One-way password-hashing boundary. */
public interface PasswordHashingPort {

    /** Hashes a request-scoped password into a persistable verifier. */
    PasswordVerifier hash(Password password);

    /** Verifies a request-scoped password against a stored verifier. */
    boolean matches(Password password, PasswordVerifier verifier);
}
