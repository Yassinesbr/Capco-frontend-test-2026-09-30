package com.kyc.identityaccess.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A normalized applicant email address. */
public record EmailAddress(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Validates and normalizes an email address. */
    public EmailAddress {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("email address is invalid");
        }
    }
}
