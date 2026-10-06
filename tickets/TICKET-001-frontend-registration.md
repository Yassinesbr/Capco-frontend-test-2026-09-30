# TICKET-001: Applicant registration (frontend)

## Scope

Implement the Applicant registration experience in the frontend only. Do not change Java backend code. Use the existing API contract: [`frontend/public/openapi.json`](../frontend/public/openapi.json), especially `POST /api/v1/applicant-accounts`.

## Acceptance criteria

- Provide a registration form for email, password, and password confirmation.
- Apply the registration validation requirements defined in the API contract.
- Submit using the JSON:API media type and request shape defined in OpenAPI.
- Handle client-side and API validation failures with understandable, accessible feedback. Show useful feedback for duplicate email, rate limiting, network failure, or other unexpected errors.
- On success, confirm account creation and provide a link or button to the Applicant sign-in page. Registration does not sign the user in.
- Keep the page accessible.

## Out of scope

Java/backend changes, changing password policy or API contract, and login implementation.
