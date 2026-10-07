# Non-functional requirements (NFRs) plan

These requirements describe the capstone demo, not a production service-level agreement. “Target” is the intended
acceptance criterion; “Status” distinguishes existing design or evidence from work that still needs proof. Record
release blockers and exceptions in the [risk register](risk-register.md), and link final evidence from the
[defense evidence index](../defense/evidence-index.md).

| ID | Area | Numeric target for the demo | How we check it | Status |
| --- | --- | --- | --- | --- |
| NFR-01 | Security | **100%** of protected API routes reject requests without a valid JWT (401); role-restricted routes reject an authenticated user without the required role (403). Issued JWTs expire after **30 minutes**. No secrets are committed. | Run backend tests for anonymous, invalid, expired and wrong-role requests against each protected route; verify token expiry; require the CI secret scan to pass and inspect its report. Review against [ADR 0006](adrs/0006-use-self-issued-jwts-for-auth.md). | JWT design and tests are specified in ADR 0006; retain CI and deployed smoke evidence. |
| NFR-02 | Traceability | **100%** of API requests have a correlation ID in the response and request logs; interaction-creation events carry the same ID. Traces must not include interaction summaries. | Send a request with a known `X-Correlation-ID`; compare the response, API logs and emitted event. Repeat without a supplied ID and confirm a generated ID is used. Search logs/events for the test summary and confirm it is absent. | Correlation behavior is specified in the contract and architecture; end-to-end deployed evidence remains to be captured. |
| NFR-03 | Recoverability | Roll back a failed release to the previous image digest and restore passing smoke checks within **10 minutes** of starting rollback. No successful database write is lost after an API restart. | In the target environment, time a rollback through the release process, record old/new digests, rerun readiness and authenticated/denied-path smoke, and verify a committed test interaction remains after restarting the API. Record evidence in the [CI/CD runbook](capstone-cicd-runbook.md). | Rollback and persistence checks are defined; target-environment proof is tracked in the [risk register](risk-register.md). |
| NFR-04 | Performance | For the seeded demo dataset, customer search, profile/timeline reads and interaction writes each complete in **2 seconds or less at p95**, excluding initial application startup. | Warm up the app, make at least **30 requests per operation**, calculate p95 from client-observed response times, and record environment, dataset and results in the evidence index. | No baseline or repeatable measurement is recorded yet. |
| NFR-05 | Operability | Both liveness and readiness endpoints return healthy responses within **5 seconds** when the service is healthy. **100%** of releases must pass the existing frontend and backend CI checks before deployment; deploys use the tested image digest. | Request both health endpoints and time responses; confirm readiness fails when a required dependency is unavailable if that behavior is supported. Check required CI status and compare the deployed digest with the CI artifact manifest. | CI and digest flow are documented in the [CI/CD runbook](capstone-cicd-runbook.md); deployed evidence is tracked in the [risk register](risk-register.md). |

## Supporting requirements

- **Privacy and data handling:** Use synthetic fixtures only. Kafka events must not contain interaction summaries, customer names or emails, and logs must not contain free-text summaries. Check emitted events and application logs during the traceability test.
- **Data integrity:** Invalid or unauthorized writes change no data. Database commit is authoritative; Kafka publication occurs after commit and may fail without undoing the interaction. Verify with negative-path and Kafka-failure tests per [ADR 0005](adrs/0005-publish-events-after-commit.md).
- **Accessibility and usability:** Core workflows are keyboard usable, controls have accessible names and visible focus, and loading, empty and failure states are distinguishable. Manually exercise login, search, profile and interaction workflows.
- **Compatibility and responsive behavior:** Support current stable Chrome and Edge at desktop and narrow/mobile viewport widths. Smoke the core workflow in both browsers and inspect representative viewport sizes.

## Scope and review

- These targets are for the capstone's synthetic-data demo. They do not establish production availability, security certification, or capacity for real customer traffic.
- Before the defense, replace pending status notes with links to evidence or document a specific exception and owner in the risk register.
- Revisit targets if deployment constraints, the demo workload, or the product scope materially changes.
