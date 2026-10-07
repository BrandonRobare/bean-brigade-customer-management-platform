# ADR 0007: Serve the Angular UI from its own nginx image

Status: implemented locally, 2026-10-06; CI publication and live Ingress verification pending (#68, #4).

The hardcoded `localhost:8080` API URL would point at the demo viewer's computer. We use a separate unprivileged
`crm-ui` nginx image and a relative API URL, with one public host and path routing. This follows Lab 51 Step 2's
nginx packaging and preserves one Angular build across environments. We reject bundling the UI into the API and
separate UI/API hosts: the former couples releases, the latter adds CORS and environment-specific URLs.

The Dockerfile copies the already-built `dist/lab50-crm-ui/browser` artifact and never invokes npm. The nginx base
is pinned by digest, runs as UID/GID 101 on port 8080 and supports a read-only root with writable `/tmp`. SPA
deep links fall back to `index.html`; API/Actuator paths and missing static assets return 404 rather than HTML.
HTTPS Ingress routes `/api` and `/actuator` to Spring Boot and `/` to nginx without rewriting paths. HTTP Ingress
routes to nginx solely to return 308 to HTTPS, using the trusted proxy's forwarded scheme; no Traefik Middleware
permission is required. A trusted certificate and actual controller configuration still need live verification.

Local `ng serve` uses the [Angular development proxy](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server)
for `/api/**` and `/actuator/**`. Production uses Ingress. nginx does not proxy API traffic.

Cost: two images must be scanned, published and recorded as a compatible release pair. One replica is the demo
default; it is not high availability. The [unprivileged image](https://github.com/nginx/docker-nginx-unprivileged)
needs a writable `/tmp`, not a privileged container. This k3s manifest is not yet OpenShift SCC validation.

Confirmation: `npm run build -- --configuration production`, then `bash scripts/check-ui.sh`. The check starts a
real non-root/read-only container, verifies UI health and a deep link, rejects API/static-file fallbacks and checks
the HTTPS redirect. CI scan/digest evidence and live same-host routing remain later steps. Angular's unfinished
login, HTTP service and customer screens are separate application work.
