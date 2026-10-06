CREATE TABLE internal_staff_sessions (
    session_id_hash TEXT NOT NULL PRIMARY KEY,
    staff_account_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('REVIEWER', 'ADMINISTRATOR')),
    created_at TEXT NOT NULL,
    idle_expires_at TEXT NOT NULL,
    absolute_expires_at TEXT NOT NULL,
    revoked_at TEXT
);

CREATE INDEX internal_staff_sessions_active_expiry_idx
    ON internal_staff_sessions (idle_expires_at, absolute_expires_at)
    WHERE revoked_at IS NULL;
