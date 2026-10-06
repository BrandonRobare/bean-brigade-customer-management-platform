# Evidence index (starter)

Every slide claim must point here. Paths relative to `lab52-capstone/` (or your Lab 48–51 trees) unless noted.

| Claim | Artifact path | Lab | Scrubbed? |
| ----- | ------------- | --- | --------- |
| Product outcome | Lab 48 `docs/architecture.md` | 48 | Y/N |
| CAP-12 backlog | Lab 48 `docs/backlog.md` | 48 | Y/N |
| Backend demo | Lab 49 `docs/backend-demo.md` | 49 | Y/N |
| Verify log | Surefire / CI URL | 49 | Y/N |
| UI→PostgreSQL | Lab 50 `docs/frontend-persistence-demo.md` | 50 | Y/N |
| Flyway | Lab 50 `V1__crm_schema.sql` | 50 | Y/N |
| Pipeline / digest | Lab 51 `docs/capstone-cicd-runbook.md` | 51 | Y/N |
| Dependency scan + triage | `docs/security-findings.csv` (dc-001 to dc-007), PR #59, main run 37382274523 (`dependency-check-report` artifact, expires 2027-01-03) | 51 | Y |
| SAST (Semgrep CE) | `reports/semgrep-report-pr61.json`, `docs/security-findings.csv` (sg-001, sg-002), PR #61 run 37390512731 | 51 | Y |
| Secrets scan (gitleaks) | `capstone-ci.yml` `secrets` job, PR #62 run 37393428246, `main` run 37393741147, required check in `protect-main` | 51 | Y |
| Rollback | Lab 51 runbook / `oc rollout undo` | 51 | Y/N |
| Deny 401/404 | notes/lab-50 or lab-51 | 50–51 | Y/N |

## TODO

- [ ] Add real relative paths after Labs 48–51 artifacts exist
- [ ] Remove tokens from any linked screenshots
