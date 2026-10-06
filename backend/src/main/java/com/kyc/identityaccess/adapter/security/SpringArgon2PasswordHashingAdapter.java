package com.kyc.identityaccess.adapter.security;

import com.kyc.identityaccess.application.port.out.PasswordHashingPort;
import com.kyc.identityaccess.domain.Password;
import com.kyc.identityaccess.domain.PasswordVerifier;
import java.util.Arrays;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Spring Security Argon2id adapter using the OWASP baseline work factors. */
public final class SpringArgon2PasswordHashingAdapter implements PasswordHashingPort {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIBIBYTES = 19 * 1024;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH,
            HASH_LENGTH,
            PARALLELISM,
            MEMORY_KIBIBYTES,
            ITERATIONS);

    /** {@inheritDoc} */
    @Override
    public PasswordVerifier hash(final Password password) {
        return new PasswordVerifier(encoder.encode(asShortLivedString(password)));
    }

    /** {@inheritDoc} */
    @Override
    public boolean matches(final Password password, final PasswordVerifier verifier) {
        return encoder.matches(asShortLivedString(password), verifier.encodedValue());
    }

    private static String asShortLivedString(final Password password) {
        char[] characters = password.copyCharacters();
        try {
            return new String(characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }
}
