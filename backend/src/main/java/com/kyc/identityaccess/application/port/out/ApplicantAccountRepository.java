package com.kyc.identityaccess.application.port.out;

import com.kyc.identityaccess.domain.ApplicantAccount;
import com.kyc.identityaccess.domain.EmailAddress;
import java.util.Optional;

/** Persistence boundary for applicant accounts. */
public interface ApplicantAccountRepository {

    /** Returns whether an account already has the supplied normalized email address. */
    boolean existsByEmail(EmailAddress email);

    /** Returns the Applicant account for a normalized email address when it is registered. */
    Optional<ApplicantAccount> findByEmail(EmailAddress email);

    /** Persists a newly registered applicant account. */
    void save(ApplicantAccount account);
}
