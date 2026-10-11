# Applicant workflow browser tests

Install the dependencies from the repository root, then install Chromium:

```powershell
npm ci
npx playwright install chromium
$env:JAVA_HOME = 'C:\path\to\jdk-25'
npm run test:e2e --workspace frontend
```

Run only the applicant scenarios with:

```powershell
npm run test:e2e --workspace frontend -- applicant-journey.spec.ts
```

The launcher starts its own frontend (13000) and backend (18080). It resets
`backend/target/e2e/identity-access-e2e.db` and stores test uploads in
`backend/target/e2e/documents`. It does not use the development database or
development document directory. Both servers stop when the run ends. An explicit
`PLAYWRIGHT_BROWSERS_PATH` is respected; otherwise Playwright uses its standard
browser installation location.

The applicant journey uses a unique account and the real Java API to verify
registration without automatic login, session cookies, both saved form steps,
evidence upload, logout/login and refresh persistence. Separate scenarios
intercept save responses to deterministically verify server failure and expired
session feedback. Existing component tests additionally cover form-version
conflicts.

Keyboard scenarios verify route-heading focus, the skip link, linked validation
errors, focus trapping and restoration, Escape cancellation, browser back,
refresh cancellation, and explicit confirmation before logout. A mobile-width
scenario checks for horizontal overflow. The warning includes both edited
answers and a document selected but not uploaded; saving remains explicit.

The React Aria dialog is used for navigation within the application and logout.
Refresh, tab closure and navigation to another website use the browser's native
`beforeunload` warning. Its wording cannot be customized, and browsers may not
show it without prior interaction or in some mobile termination scenarios.

Traces and screenshots are retained on failure in the Playwright results
directory. These automated checks do not replace a manual screen-reader,
contrast, zoom and keyboard audit. Final application submission remains outside
these tickets and is deliberately not exercised as a real submission.
