# Technical Q&A — prepared answers

> **TODO:** Fill claim → evidence → trade-off → next-step answers before the panel.

## Why PostgreSQL?

TODO

## Why Kafka after persist?

TODO

## What if the panel asks for the JWT?

TODO — never paste live tokens into slides.

## How do you prove the same artifact reached staging?

CI builds the JAR once, the image wraps that exact JAR, and every environment deploys the same `@sha256:` digest.

- **Evidence:** `main` run 37481443017. `SHA256SUMS` has the JAR's hash; the `image` job re-checks it before
  `docker build`, and the Dockerfile only copies the JAR. `artifact-manifest.json` ties commit `9125d07`, JAR
  `1013e386…` and digest `sha256:d30e1ce3…` together (full values in `defense/evidence-index.md`).
- **Trade-off:** the manifest proves what CI built, not what's running.
- **Next step:** TODO: once `promote` runs, show the staging pod's image digest matching the manifest.

## Why is the Angular client not the security boundary?

TODO — interceptor vs API validation (Lab 36 / Lab 50).

## What fails closed?

TODO — 401 / 404 / readiness

