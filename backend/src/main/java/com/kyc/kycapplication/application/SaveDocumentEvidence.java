package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.DocumentStorage;
import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.DocumentStorageReference;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Objects;

/** Validates and records accepted identity-document evidence for an owned draft. */
public final class SaveDocumentEvidence {

    public static final long MAX_SIZE = 10L * 1024L * 1024L;

    private final GetApplicationForm getApplicationForm;
    private final KycApplicationFormRepository formRepository;
    private final DocumentStorage documentStorage;
    private final Clock clock;

    /** Creates the document command at the application/storage boundary. */
    public SaveDocumentEvidence(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final DocumentStorage documentStorage,
            final Clock clock) {
        this.getApplicationForm = new GetApplicationForm(applicationRepository, formRepository);
        this.formRepository = Objects.requireNonNull(formRepository);
        this.documentStorage = Objects.requireNonNull(documentStorage);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Stores a bounded accepted image and returns the new owner-scoped form state. */
    public GetApplicationForm.ApplicationFormResult save(
            final ApplicantId ownerId, final ApplicationId applicationId, final InputStream content,
            final String mediaType, final long size) throws IOException {
        if (size < 0 || size > MAX_SIZE) {
            throw new InvalidDocumentEvidenceException("Document size is not accepted.");
        }
        if (!"image/jpeg".equalsIgnoreCase(mediaType) && !"image/png".equalsIgnoreCase(mediaType)) {
            throw new InvalidDocumentEvidenceException("Document media type is not accepted.");
        }
        GetApplicationForm.ApplicationFormResult current = getApplicationForm.find(ownerId, applicationId);
        DocumentStorage.StoredDocument stored = documentStorage.store(content, mediaType, size);
        try {
            ApplicationForm updated = current.form().withDocumentEvidence(new DocumentStorageReference(
                    stored.storageKey(), stored.mediaType(), stored.size(), stored.digest()), clock.instant());
            formRepository.save(updated);
            return new GetApplicationForm.ApplicationFormResult(current.application(), updated);
        } catch (RuntimeException failure) {
            try {
                documentStorage.delete(stored.storageKey());
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
