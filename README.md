# KYC Application — Technical Test

This repository is a technical test for frontend development. It provides a small, realistic product context and a running API so a developer can demonstrate how they build user-facing workflows against an existing contract.

## Technical test scope

The repository includes tickets in [`tickets/`](tickets/) describing frontend work. Follow those tickets and their acceptance criteria. Keep changes in the frontend; the Java backend and API contract are provided for integration and are not part of the implementation scope.

### Assessment expectations

AI-assisted development is expected. Review AI output and take responsibility for every change. Be ready to explain and justify submitted code.

The provided code is a basic starting point, is not intended to represent high-quality final code, and may be improved. The assessment focuses on frontend code; visual design and design changes are not assessed. Keep the application responsive on desktop and mobile.

Code will be reviewed for quality, maintainability, accessibility, and fit with the existing architecture. A working application alone is not enough. The developer's process and decisions will be discussed and challenged during the interview, including planning, implementation, verification, and AI tool use.

Create a Git repository for your submission and make separate commits for meaningful changes. Use clear commit messages so reviewers can follow your work and decisions.

Implement the requested work to follow WCAG accessibility standards. The existing application is not currently WCAG-compliant; treat this as a gap to improve, not as the acceptance standard.

## Business context

Know Your Customer (KYC) is the process a financial business uses to collect and review information about a customer before providing regulated services. This prototype models that process with applicants who provide information and internal staff who review it. Applicants enter identity and contact details, provide identity-document evidence, and consent to the application being reviewed.

### Product goal

Give applicants a clear way to create an account, complete and save a KYC application, and return to their saved work. Give internal reviewers a workspace to inspect submitted applications and update their status.

The initial product includes account creation and sign-in, session and role controls, a two-step applicant form, saved answers, submission, reviewer views, and application status tracking. The main screens should work on desktop and mobile and use a consistent component library.

## Technology

- **Frontend:** React Router 7, React 19, TypeScript, React Aria Components, and TanStack Query.
- **Backend:** Java 25 and Spring Boot 4, with a versioned JSON:API HTTP interface.
- **Database:** SQLite, initialized and updated by Flyway migrations.
- **API documentation:** OpenAPI 3.1, served through Swagger UI.
- **UI library:** shared React Aria components in `DS/`, documented in Storybook. These are basic components and may be improved when needed for the implementation.

The frontend development server proxies `/api/v1` requests to the backend. By default, frontend runs on port `3000` and backend runs on port `8080`.

## Schema organization

The repository separates the application into three folders:

- **`backend/`** contains the Java and Spring Boot API, business logic, and database migrations. It owns server-side behavior and persistence.
- **`frontend/`** contains the React Router application, routes, and user workflows. It calls the backend through the `/api/v1` HTTP interface. The API contract is available in `frontend/public/openapi.json`.
- **`DS/`** contains the shared React component library and Storybook stories. The frontend imports these components; keep reusable UI components here and application-specific screens in `frontend/`.

The root `package.json` manages the frontend and `DS/` as npm workspaces. The backend builds separately with the Maven Wrapper.

### Frontend domain glossary

Frontend names describe the KYC workflow and the people who use it:

- **Applicant:** Person who creates and completes a KYC application.
- **Application:** Applicant's KYC record, including personal details, identity and address information, and review status.
- **Reviewer:** Internal staff member who checks submitted applications.
- **Journey:** Applicant-facing flow for starting or resuming an application.
- **Step:** One part of the application form, such as `personal-details` or `identity-and-address`.
- **Current application:** Applicant's active draft, reached at `/applications/current` in this prototype.
- **Submitted:** Application sent for review; distinct from a draft.
- **Status:** Application lifecycle state, such as `draft`, `submitted`, `in-review`, `approved`, or `rejected`.
- **Presentation:** UI component or data shaped for display. For example, applicant journey presentation data is separate from route behavior.
- **Fixture:** Sample data used by the prototype or tests. It does not imply persisted backend data.

Names such as `ApplicantJourneyPresentation` identify a user and workflow. Route modules handle navigation and page behavior; presentation components render the interface. Treat prototype fixtures as examples unless a ticket or API contract says otherwise.

## API documentation

Start the application using the steps below, then open [Swagger UI](http://127.0.0.1:3000/api-docs). The raw OpenAPI document is available at [OpenAPI JSON](http://127.0.0.1:3000/openapi.json). Swagger UI is read-only in this prototype; use it to inspect endpoints, request schemas, and responses.

The checked-in contract is [`frontend/public/openapi.json`](frontend/public/openapi.json).

## Run locally

### Requirements

- Node.js 26 and npm.
- JDK 25, with `JAVA_HOME` set to its installation directory.

The Maven Wrapper is included, so a separate Maven installation is not required. The first startup may download Maven and Java dependencies.

The `npm run dev:app` command starts both the frontend and backend servers.

### Windows PowerShell

From the repository root:

```powershell
npm ci
$env:JAVA_HOME = 'C:\path\to\jdk-25'
npm run dev:app
```

Replace the example path with your JDK 25 installation path. Keep `JAVA_HOME` set in the same PowerShell session used to start the application.

### macOS or Linux

From the repository root, set `JAVA_HOME` to your JDK 25 directory, then run:

```sh
export JAVA_HOME="/path/to/jdk-25"
npm ci
npm run dev:app
```

For macOS, `/usr/libexec/java_home -v 25` can provide the JDK path when JDK 25 is installed. On Linux, use the installed JDK directory, for example `/usr/lib/jvm/java-25-openjdk-amd64`.

The launcher creates `backend/data/` for the local SQLite database. Open the frontend at [http://127.0.0.1:3000](http://127.0.0.1:3000). The backend listens at [http://127.0.0.1:8080](http://127.0.0.1:8080). Press `Ctrl+C` in the launcher terminal to stop both services.

## Development commands

Run from the repository root:

```sh
npm run typecheck
npm test
npm run build
npm run storybook
```

Run frontend browser end-to-end tests with:

```sh
npm run test:e2e --workspace frontend
```

Run frontend tests directly with `npm test --workspace frontend`. Run the root UI library quality checks with `npm run quality`.

## Local data

The development database is stored at `backend/data/identity-access.db`. Remove this local database file only when you want to reset local application data.
