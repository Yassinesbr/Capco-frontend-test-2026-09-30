DROP TRIGGER IF EXISTS kyc_application_forms_insert_only_for_drafts;
DROP TRIGGER IF EXISTS kyc_application_forms_update_only_for_drafts;

CREATE TABLE kyc_applications_v5 (
    id TEXT NOT NULL PRIMARY KEY,
    applicant_account_id TEXT NOT NULL,
    lifecycle TEXT NOT NULL CHECK (lifecycle IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
    current_step TEXT NOT NULL CHECK (current_step IN ('PERSONAL_DETAILS', 'IDENTITY_AND_ADDRESS')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    submitted_at TEXT,
    approved_at TEXT,
    CHECK ((lifecycle = 'DRAFT' AND submitted_at IS NULL AND approved_at IS NULL)
        OR (lifecycle IN ('SUBMITTED', 'IN_REVIEW') AND submitted_at IS NOT NULL AND approved_at IS NULL)
        OR (lifecycle = 'REJECTED' AND submitted_at IS NOT NULL AND approved_at IS NULL)
        OR (lifecycle = 'APPROVED' AND submitted_at IS NOT NULL AND approved_at IS NOT NULL)),
    FOREIGN KEY (applicant_account_id) REFERENCES applicant_accounts (id)
);

INSERT INTO kyc_applications_v5 (id, applicant_account_id, lifecycle, current_step, created_at, updated_at,
        submitted_at, approved_at)
SELECT id, applicant_account_id, lifecycle, current_step, created_at, updated_at, submitted_at, NULL
FROM kyc_applications;

CREATE TABLE kyc_application_forms_v5 (
    application_id TEXT NOT NULL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    name TEXT,
    date_of_birth TEXT,
    country TEXT,
    nationality TEXT,
    email TEXT,
    phone TEXT,
    consent_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (consent_confirmed IN (0, 1)),
    document_type TEXT,
    document_number TEXT,
    document_country TEXT,
    expiry TEXT,
    street TEXT,
    city TEXT,
    postal TEXT,
    residential_country TEXT,
    document_evidence_present INTEGER NOT NULL DEFAULT 0 CHECK (document_evidence_present IN (0, 1)),
    document_storage_key TEXT,
    document_media_type TEXT,
    document_size INTEGER,
    document_digest TEXT,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (application_id) REFERENCES kyc_applications_v5 (id) ON DELETE CASCADE
);

INSERT INTO kyc_application_forms_v5
SELECT application_id, version, name, date_of_birth, country, nationality, email, phone, consent_confirmed,
    document_type, document_number, document_country, expiry, street, city, postal, residential_country,
    document_evidence_present, document_storage_key, document_media_type, document_size, document_digest, updated_at
FROM kyc_application_forms;

DROP TABLE kyc_application_forms;
DROP TABLE kyc_applications;
ALTER TABLE kyc_applications_v5 RENAME TO kyc_applications;
ALTER TABLE kyc_application_forms_v5 RENAME TO kyc_application_forms;

CREATE UNIQUE INDEX kyc_applications_one_active_draft_per_owner_idx
    ON kyc_applications (applicant_account_id) WHERE lifecycle = 'DRAFT';
CREATE INDEX kyc_applications_lifecycle_approved_at_idx
    ON kyc_applications (lifecycle, approved_at);

CREATE TRIGGER kyc_application_forms_insert_only_for_drafts
BEFORE INSERT ON kyc_application_forms
WHEN (SELECT lifecycle FROM kyc_applications WHERE id = NEW.application_id) <> 'DRAFT'
BEGIN
    SELECT RAISE(ABORT, 'submitted application is immutable');
END;

CREATE TRIGGER kyc_application_forms_update_only_for_drafts
BEFORE UPDATE ON kyc_application_forms
WHEN (SELECT lifecycle FROM kyc_applications WHERE id = OLD.application_id) <> 'DRAFT'
BEGIN
    SELECT RAISE(ABORT, 'submitted application is immutable');
END;
