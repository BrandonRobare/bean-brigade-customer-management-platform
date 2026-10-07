# NFRS plan 

| ID | Category | Requirement | Verified by |
|----|----------|-------------|-------------|
| NFR-01 | Security | Every /api/v1/** call without a valid JWT token get 401 by default, and 403 with the wrong role | Security testing and authentication logs |
| NFR-02 | Security | No secrets in Git | Code review and secret scanning tools |
| NFR-03 | Deployability | Every environment runs the same application image | CI/CD pipeline logs and configuration |
| NFR-04 | Compatibility | v1 API will be stable and changes will only be additive; Breaking changes will go to /api/v2 | controller endpoints and `contract.md` changelog |
| NFR-05 | Performance | Search queries will use an index scan, not a sequential scan | Database performance monitoring and query analysis |