# ADR 0006: Use self-issued JWTs for sign-in and roles

**In short:** In the context of sign-in and the AGENT / ADMIN roles, facing a brief that requires JWT and a course that
gives us no identity provider, we decided for JWTs issued and checked by our own API and against Keycloak, Auth0 and the
Lab 28 stub token, to achieve signed, expiring tokens with nothing extra to deploy or reach at PNC, accepting that we
hold the signing key and the demo passwords ourselves.

## Context and Problem Statement

The brief requires JWT authentication with AGENT and ADMIN roles, and the API, not Angular, has to enforce them. The
starter's `DemoBearerFilter` accepts one fixed string, `lab-demo-token`, which has no roles and never expires. The
course gives us no identity provider. Who issues our tokens, and how does the API check them?

## Decision Drivers

- The brief and rubric: JWT with roles, enforced by the API, proven by 401 / 403 tests
- Module 51: a JWT resource server that denies by default
- The demo runs at PNC on a network we haven't seen
- About 20 hours of build time, and only the taught stack
- No secrets in Git

## Considered Options

1. **Self-issued JWT:** our API signs tokens at a login endpoint and checks them itself.
2. **Keycloak:** a real identity provider, but a second container to configure and deploy to k3s, about a day of
   work, and one more thing to break in the demo.
3. **Auth0:** real OIDC with no server to run, but it needs an outside account, isn't on the taught stack, and the demo
   would depend on PNC's internet.
4. **Lab 28 stub token** (`lab.<sub>.<role>.<hash>`): free, but it isn't a JWT. It never expires, and a hash of the
   secret isn't a signature.

## Decision Outcome

Chosen option: "Self-issued JWT", because it's the only option with real signed, expiring tokens and roles that adds
nothing to deploy and nothing to reach over the internet during the demo.

- `POST /api/v1/auth/login` signs an RS256 JWT (`sub`, `roles`, `exp` 30 minutes out) with a private RSA key, for two
  in-memory demo users: `agent1` (AGENT) and `admin1` (ADMIN).
- Spring Security's OAuth2 resource server checks the token on every other `/api/**` call using only the public key,
  and anything not explicitly allowed is denied.
- The ADMIN-only endpoints also carry `@PreAuthorize("hasRole('ADMIN')")`, so the role check sits on the endpoint
  itself as well as in the URL rules, as Module 49 teaches.
- The private key (`JWT_PRIVATE_KEY`) and the demo passwords come from the environment, never from Git. The public key
  (`JWT_PUBLIC_KEY`) isn't a secret.
- Angular keeps the token in memory only. Its guard hides screens; the API decides.
- `lab-demo-token` keeps working under the `dev` profile until the login page lands (Tue 10/6), then it's removed.

We chose RS256 over HS256, the single shared secret Lab 28 used. With RS256, checking a token needs only the public
key, and it's what Spring and identity providers like Keycloak and Auth0 use by default. Spring Security's own JWT login
sample takes the same approach.

The endpoint shapes go in `docs/contract.md`, and the access rules and wiring go in the security section of
`docs/architecture.md`.

### Consequences

- Good, because nothing extra runs in the demo: no identity provider container, no outside account, no internet
  dependency.
- Good, because the API uses Spring's standard resource server to check tokens, with the same algorithm an identity
  provider uses. Moving to Keycloak or Auth0 later means a config change (`issuer-uri`) and deleting the token
  endpoint, while the rules and tests stay the same.
- Good, because only the token endpoint touches the private key. Checking a token can't create one, and a captured
  token can't be used to guess the key the way a weak HS256 secret can.
- Good, because tokens expire: a leaked one works for 30 minutes at most.
- Bad, because we hold the private key. If `JWT_PRIVATE_KEY` leaks, anyone can mint an ADMIN token until we replace the
  key pair, and replacing it signs everyone out. It goes in the risk register.
- Bad, because a key pair is more setup than one secret string: generate it, mount it as a Kubernetes Secret, and
  have tests generate their own.
- Bad, because there's no refresh, revocation, lockout or login rate limit. These are non-claims: a token stays valid
  until it expires, even after logout.
- Neutral, because the contract gains one endpoint, which Bryan approves.

### Confirmation

- MockMvc tests: no token → 401, bad signature → 401, expired → 401, wrong password → 401, AGENT on an ADMIN route
  → 403, right role → 200.
- The smoke script checks that an anonymous call to the deployed app gets 401.
- A row in `defense/evidence-index.md` points at the test report.
- The review of the security filter chain PR checks it against this ADR.

## More Information

- Revisit if: a second service needs to check tokens (publish the public key as a JWKS endpoint), or real users or SSO
  are needed (switch to Keycloak and keep the resource-server rules).
- Confidence: high for the demo, low as a production design.