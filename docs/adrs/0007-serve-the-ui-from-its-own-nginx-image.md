# ADR 0007: Serve the Angular UI from its own nginx image

Status: proposed, waiting on Chad (#68). This PR implements it: the image, nginx config and CI pair job pass local
checks; CI publication and live Ingress verification are pending (#68, #4).

**In short:** In the context of putting the Angular UI on the course cluster, facing an API URL hardcoded to
`localhost:8080` and a rule of one Angular build for every environment, we decided for a separate `crm-ui` nginx image
behind the same Ingress host as the API, and against bundling the build into `crm-api` or giving the UI and API their
own hosts, to achieve one origin and separate UI and API releases, accepting a second image to build, scan and deploy.

## Context and Problem Statement

`environment.ts` hardcodes `apiBaseUrl: 'http://localhost:8080'`, so a production build would call the agent's own
laptop. The environment strategy fixes that with a relative API URL and the UI and API on one origin, but left open
how the UI is served. The course cluster is k3s, so routing is an Ingress (Traefik), not an OpenShift Route. How does
the Angular build reach the browser in the cluster?

## Decision Drivers

- The brief's container diagram draws the Angular frontend and the Spring Boot API as separate boxes
- Lab 51: "bake into nginx image in the container job" (Step 2) and "frontend image if split" (Step 3)
- One Angular build for every environment, no CORS in production
- Deploy and roll back by digest, without a UI change redeploying the API
- The namespace quota: 2 CPU and 3Gi of requests for everything
- CP3 on Thu 10/8

## Considered Options

1. **`crm-ui` nginx image, Ingress path routing:** `/api` goes to `crm-api`, everything else to `crm-ui`.
2. **Bundle into `crm-api`:** copy `dist/` into Spring Boot's `static/` when the image is built. One image, one digest.
3. **Two hosts with CORS:** `crm-ui` and `crm-api` each get their own Ingress host, and the API allows the UI's origin.

## Decision Outcome

Chosen option: "`crm-ui` nginx image, Ingress path routing", because it matches the brief and Lab 51, gives one origin
without any proxy config in nginx, and keeps the UI and API on separate digests.

- `crm-ui` is an unprivileged nginx base (non-root, port 8080) plus the `dist/` artifact from the `frontend` job. It
  serves files only, with an SPA fallback (`try_files $uri $uri/ /index.html`).
- One Ingress host: `/api` to the `crm-api` Service, `/` to the `crm-ui` Service.
- The `image` job builds, scans and pushes `crm-ui` the same way as `crm-api`, and `artifact-manifest.json` lists both
  digests.

### Consequences

- Good, because Angular calls a relative `/api`, so one build works everywhere and production needs no CORS.
- Good, because a UI fix doesn't rebuild or redeploy the API, and rolling back one doesn't touch the other.
- Good, because the routing lives in one Ingress, and nginx only serves files. nginx is small next to the quota.
- Bad, because it's a second Dockerfile, image build, Trivy scan, digest and Deployment.
- Bad, because without the SPA fallback, reloading a deep link like `/customers/CUS-1001` returns nginx's 404.
- Bad, because `ng serve` on :4200 can't use a relative `/api` on its own. Local dev needs a dev-server proxy
  (`proxy.conf.json`) or keeps the `localhost:8080` URL and CORS for `localhost:4200` only.

### Confirmation

- CI builds and Trivy-scans `crm-ui` on every PR and pushes it on `main`.
- The smoke script checks the Ingress host twice: `/` returns `index.html`, and `/api/...` returns JSON.
- A row in `defense/evidence-index.md` points at the run with both digests.
- Locally: `bash scripts/check-ui.sh` runs the real non-root, read-only container and checks health, a deep link,
  API/static-file 404s and the HTTPS redirect.

## Pros and Cons of the Options

### Bundle into `crm-api`

- Good, because it's one image, one digest and fewer manifests.
- Bad, because every UI change rebuilds and redeploys the API, and the `image` job has to wait for the frontend build.
- Bad, because Spring has to forward Angular's routes to `index.html`, or deep links 404 there instead.
- Bad, because it doesn't match the brief's diagram.

### Two hosts with CORS

- Good, because there are no path rules.
- Bad, because the API URL differs by host, so one build needs config at runtime.
- Bad, because production needs CORS config, and two hosts means two Ingress rules to secure.

## More Information

- Revisit if: the second image can't land before CP3 (then bundle into `crm-api` as the fallback), or the UI moves to a
  CDN.
- Confidence: high.
- Links: #68, #4, [environment strategy](../environment-strategy.md) (One Angular build), [Actions plan](../github-actions-plan.md) (`image` job), Lab 51 Steps 2 and 3
