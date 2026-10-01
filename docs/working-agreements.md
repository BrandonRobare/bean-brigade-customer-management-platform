# Working Agreements

## Team

| Person  | role               | Lane               | Owns                                                                                                                |
|---------|--------------------|--------------------|---------------------------------------------------------------------------------------------------------------------|
| Brandon | team lead          | Security + release | JWT / RBAC, 401 / 403 tests, CORS, CI, image + digest, OpenShift, CD, probes, smoke, rollback                       |
| Bryan   | technical lead     | API + persistence  | customer + interaction endpoints, validation, Problem Details, Flyway `V2`+, JPA, tests                             |
| Carter  | documentation lead | Kafka              | producer, consumer, DLT, correlation ID, Kafka ops                                                                  |
| Chad    | presentation lead  | Angular            | search, profile, interaction form + timeline, interceptors, loading / error / empty states, accessibility, Selenium |


## How We Work

### Board

- **Parents** are stories (`CAP-12`) or deliverable groups. They close by hand, after the end-to-end check passes and
  the evidence-index row exists.
- **Sub-issues** are one PR, one person, at most a day. They close when their PR merges (`Closes #N`). Work that isn't
  code closes by hand, with a link to what it produced.
- **Lane** is the area; the **assignee** is the person.
- **Priority:** P0 never cut, P1 on the cut list, P2 stretch. No milestones and no due dates in issue text.
- Acceptance criteria live in `docs/backlog.md`; issues link to it.

### Branches and PRs

- Branch from fresh `main`: `feat/CAP-12-interaction-form` (also `fix/`, `test/`, `docs/`, `ci/`, `chore/`).
- Open a draft PR the same day you start. One concern per PR, under about 400 changed lines.
- Title it like `CAP-12: record interaction from profile page` and fill in the PR template.
- After approval and green checks, the author squash-merges.
- Update from `main` before asking for review. Only force-push your own branch. Never hand-merge `package-lock.json`:
  take `main`'s copy and run `npm install`.

### Definition of Done

Merged to `main` with green CI · a happy-path and a failure-path test · docs updated · a row in
`defense/evidence-index.md` if it proves something.

### Secrets and Data

Secrets live only in local `.env` files and GitHub Environment secrets. Data is fixtures only: `CUS-1001`, `CUS-1002`,
`CUS-9999`, `@example.test` emails. A committed secret gets rotated, not just deleted.


