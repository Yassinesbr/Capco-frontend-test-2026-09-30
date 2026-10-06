package com.kyc.identityaccess.application;

/** Generic public authentication outcome that never identifies which credential was invalid. */
public final class AuthenticationFailedException extends RuntimeException {

    /** Creates the deliberately non-enumerating authentication failure. */
    public AuthenticationFailedException() {
        super("Invalid email or password");
    }
}
