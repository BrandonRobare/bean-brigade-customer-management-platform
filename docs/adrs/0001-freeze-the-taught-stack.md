---
status: proposed
date: 2026-10-06
decision-makers: [Brandon, Bryan, Carter, Chad]
---

# ADR 0001: Freeze the taught stack

Use Angular, Spring Boot REST, PostgreSQL, Kafka, GitHub Actions and Kubernetes (the course's k3s cluster) for the capstone.

## Context and Problem Statement

Labs 48–52 build one customer-management slice using the stack taught in Weeks 1–5.
Changing frameworks or delivery tools would take time away from the working demo and its evidence.
Which technologies should the team build against?

## Decision Drivers

- Follow the capstone brief and Lab 48 stack requirements.
- Reuse the course starters and skills the team has practiced.
- Keep the remaining work small enough to finish and rehearse before the defense.

## Considered Options

1. Keep the taught stack throughout the slice.
2. Substitute familiar tools such as React, Oracle, SOAP, Bitbucket Pipelines or a laptop k3s deployment.

## Decision Outcome

Choose the taught stack. The course examples and release evidence use it, so substitutions would add integration
work and require a separate ADR.

| Layer | Baseline |
| --- | --- |
| Frontend | Angular 19, TypeScript, HttpClient |
| API | Spring Boot 3.5, Java 21, Maven, REST/JSON, JWT/RBAC |
| Persistence | PostgreSQL 16, Spring Data JPA, Flyway |
| Messaging | Apache Kafka, versioned interaction events |
| Delivery | GitHub Actions, Docker images promoted by digest, k3s |

React, Oracle, SOAP, Bitbucket Pipelines and laptop k3s are outside this baseline. Compatible security updates
are allowed through normal review; this freezes the technology choices, not vulnerable patch versions.

### Consequences

- The team can share the lab examples, API contract and delivery plan.
- We give up familiar alternatives and depend on access to the course deployment environment.

### Confirmation

Review dependencies, diagrams and workflows against this table. The frontend and backend CI jobs check the builds;
Kafka publication/consumption and deployment by digest still need their own runtime evidence.

## More Information

- [Issue #8](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/8),
  [architecture](../architecture.md#stack), [environment strategy](../environment-strategy.md).
