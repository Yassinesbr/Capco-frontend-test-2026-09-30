# TICKET-002: Applicant login (frontend)

## Scope

Implement the Applicant login experience in the frontend only. Do not change Java backend code. Use the existing API contract: [`frontend/public/openapi.json`](../frontend/public/openapi.json), especially `POST /api/v1/applicant-sessions`.

## Acceptance criteria

- Provide a login form for email and password with client-side required-field validation.
- Submit credentials using the JSON:API media type and request shape defined in OpenAPI.
- On success, honor the API session cookies and route the Applicant to the journey URL returned by the API.
- Show a clear, accessible form-level error for invalid credentials. Do not indicate whether an email is registered.
- Show useful errors for malformed input, rate limiting, network failure, and unexpected API responses; keep entered email when showing errors.
- Prevent duplicate submissions while a login request is pending and expose pending/error states accessibly.

## Out of scope

Java/backend changes, registration implementation, and changing authentication/session behavior.
