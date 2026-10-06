package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.KycApplication;
import java.util.Objects;
import java.util.Optional;

/** Reads only the authenticated Applicant's current active draft. */
public final class FindCurrentApplication {

    private final KycApplicationRepository repository;

    /** Creates the query from the aggregate repository. */
    public FindCurrentApplication(final KycApplicationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /** Returns the Applicant's active draft without modifying its lifecycle or progress. */
    public Optional<KycApplication> findFor(final ApplicantId ownerId) {
        return repository.findActiveByOwner(ownerId);
    }
}
