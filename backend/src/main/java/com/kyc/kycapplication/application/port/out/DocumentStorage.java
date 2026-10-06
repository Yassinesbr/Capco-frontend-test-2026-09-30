package com.kyc.kycapplication.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/** Outbound boundary for storing Applicant document evidence outside the domain. */
public interface DocumentStorage {

    /** Stores a bounded document stream and returns only safe metadata. */
    StoredDocument store(InputStream content, String mediaType, long size) throws IOException;

    /** Removes a just-stored document when the owning transaction cannot be committed. */
    void delete(String storageKey) throws IOException;

    /** Safe reference and metadata for a stored document. */
    record StoredDocument(String storageKey, String mediaType, long size, String digest) {
    }
}
