package com.kyc.identityaccess.domain;

import java.util.Arrays;
import java.util.Objects;

/** A request-scoped raw password that must be cleared after use. */
public final class Password implements AutoCloseable {

    private final char[] characters;

    /** Validates a password against the approved account-access policy. */
    public Password(final char[] characters) {
        Objects.requireNonNull(characters, "characters must not be null");
        this.characters = Arrays.copyOf(characters, characters.length);
        if (!meetsPolicy(this.characters)) {
            close();
            throw new IllegalArgumentException("password does not meet the required policy");
        }
    }

    /** Returns a short-lived copy for a hashing adapter. */
    public char[] copyCharacters() {
        return Arrays.copyOf(characters, characters.length);
    }

    /** Clears the raw password from memory. */
    @Override
    public void close() {
        Arrays.fill(characters, '\0');
    }

    private static boolean meetsPolicy(final char[] password) {
        boolean uppercase = false;
        boolean special = false;
        for (char character : password) {
            uppercase |= Character.isUpperCase(character);
            special |= !Character.isLetterOrDigit(character);
        }
        return password.length >= 6 && uppercase && special;
    }
}
