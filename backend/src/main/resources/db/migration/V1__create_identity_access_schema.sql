CREATE TABLE applicant_accounts (
    id TEXT NOT NULL PRIMARY KEY,
    normalized_email TEXT NOT NULL UNIQUE,
    password_verifier TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role = 'APPLICANT'),
    created_at TEXT NOT NULL
);

CREATE INDEX applicant_accounts_normalized_email_idx
    ON applicant_accounts (normalized_email);

CREATE TABLE applicant_sessions (
    session_id_hash TEXT NOT NULL PRIMARY KEY,
    applicant_account_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role = 'APPLICANT'),
    created_at TEXT NOT NULL,
    idle_expires_at TEXT NOT NULL,
    absolute_expires_at TEXT NOT NULL,
    revoked_at TEXT,
    rotated_from_session_hash TEXT,
    FOREIGN KEY (applicant_account_id) REFERENCES applicant_accounts (id)
);

CREATE INDEX applicant_sessions_active_expiry_idx
    ON applicant_sessions (idle_expires_at, absolute_expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE identity_access_audit_events (
    event_id INTEGER PRIMARY KEY,
    event_type TEXT NOT NULL CHECK (event_type IN (
        'APPLICANT_REGISTERED',
        'APPLICANT_SIGNED_IN',
        'APPLICANT_SIGNED_OUT',
        'AUTHENTICATION_FAILED',
        'AUTHORIZATION_DENIED'
    )),
    outcome TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    minimized_subject_reference TEXT NOT NULL
);

CREATE INDEX identity_access_audit_events_occurred_at_idx
    ON identity_access_audit_events (occurred_at);
