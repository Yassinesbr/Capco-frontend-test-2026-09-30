# TICKET-003: Save and resume Applicant form (frontend)

## Scope

Implement frontend save and resume behavior for the ongoing Applicant KYC form. Do not change Java backend code. Follow the application and form endpoints, request schemas, authentication, and error responses in [`frontend/public/openapi.json`](../frontend/public/openapi.json).

## Acceptance criteria

- Load the signed-in Applicant's existing draft and populate every previously saved answer in the correct form fields.
- Save entered answers through the documented API when the user activates Save. Show a clear saved state only after the API confirms success.
- After logout and a later login, reload the saved draft from the API and show the previous answers. Do not rely on browser-only state as persisted data.
- Show a clear error if save fails, preserve entered values, and let the user retry without falsely indicating success.
- Handle expired sessions and API validation/conflict errors with clear user-facing feedback.
- Optionally add simple field validation and show clear errors beside invalid fields. It is okay to skip field validation for this technical test.

## Out of scope

Java/backend changes, changing form fields or persistence rules, and final application submission behavior.
