CREATE TABLE kyc_applications (
    id TEXT NOT NULL PRIMARY KEY,
    applicant_account_id TEXT NOT NULL,
    lifecycle TEXT NOT NULL CHECK (lifecycle = 'DRAFT'),
    current_step TEXT NOT NULL CHECK (current_step IN ('PERSONAL_DETAILS', 'IDENTITY_AND_ADDRESS')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (applicant_account_id) REFERENCES applicant_accounts (id)
);

CREATE UNIQUE INDEX kyc_applications_one_active_draft_per_owner_idx
    ON kyc_applications (applicant_account_id)
    WHERE lifecycle = 'DRAFT';

CREATE TABLE kyc_application_audit_events (
    event_id INTEGER PRIMARY KEY,
    event_type TEXT NOT NULL CHECK (event_type IN ('APPLICATION_STARTED', 'APPLICATION_RESUMED', 'APPLICATION_ACCESS_DENIED')),
    outcome TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    minimized_subject_reference TEXT NOT NULL
);

CREATE INDEX kyc_application_audit_events_occurred_at_idx
    ON kyc_application_audit_events (occurred_at);
