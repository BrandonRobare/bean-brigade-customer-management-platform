# AI usage close-out

The team used AI as an assistive drafting tool within the boundaries established during Lab 48. AI-assisted work remained subject to the same review, testing, security, and approval requirements as other project work.

## Allowed

AI assistance was permitted for:

- Boilerplate code drafts
- Test drafts
- HCL and YAML infrastructure sketches
- Documentation drafts

A documented example is [`docs/ai-iac-review.md`](ai-iac-review.md), where AI-assisted infrastructure suggestions were reviewed and either accepted, rejected, or hardened before use.

## Must verify

AI-assisted output was not accepted solely because it appeared correct.

- Angular changes had to compile, pass tests, and match the API contracts.
- Flyway and SQL changes had to be reviewed for PostgreSQL correctness and validated through database testing.
- Infrastructure changes were reviewed and checked with Terraform, Ansible, security scans, and deployment controls.
- Application changes still had to pass the project's normal backend, frontend, integration, and CI checks.
- Repository and secret-scanning controls were used to help ensure credentials, tokens, kubeconfig files, and other sensitive information were not committed.
- Team members reviewed project changes and supporting evidence before acceptance.

## Forbidden

AI was not authorized to:

- Receive or expose secrets, credentials, tokens, or kubeconfig files
- Apply Terraform or Ansible changes to production without the required human review and approval
- Copy proprietary third-party code into the project
- Replace required human review, testing, or project verification

## Human ownership

Final project decisions remained with the team, including:

- Application architecture and API contracts
- PostgreSQL schema and migration decisions
- Kafka topic, event, retry, DLT, and delivery design
- Authentication, authorization, and security controls
- CI/CD, infrastructure, deployment, backup, and rollback decisions
- Acceptance of project risks and security findings
- Pull-request review and merge decisions
- Deciding whether evidence was sufficient to support a defense claim

AI was used to assist the team, but final responsibility for the submitted system and its technical decisions remained with the team.
