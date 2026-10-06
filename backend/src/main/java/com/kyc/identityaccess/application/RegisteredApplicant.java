package com.kyc.identityaccess.application;

import com.kyc.identityaccess.domain.EmailAddress;
import java.util.UUID;

/** Non-secret outcome of account registration. */
public record RegisteredApplicant(UUID id, EmailAddress email) {
}
