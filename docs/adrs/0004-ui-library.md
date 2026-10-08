---
status: accepted
date: 2026-10-06
decision-makers: [Brandon, Bryan, Carter, Chad]
---

# ADR 0004: Use plain CSS for the UI

**In short:** For the Angular CRM interface, use semantic HTML and application-owned CSS rather than adding Angular Material or Bootstrap. 
The current slice has a small, focused set of screens, and plain CSS avoids a new dependency and lets the team shape the interface to the CRM without adopting a larger component system.

## Context and Problem Statement

The Angular application needs consistent, responsive screens for signing in, finding customers and managing interactions. 
The capstone has a limited scope and delivery window, and its UI can use standard browser controls and the Angular patterns already in the project. 
Which styling approach gives the team enough structure without adding unnecessary setup or learning overhead?

## Decision Drivers

- Keep the frontend aligned with the Angular stack and course starter.
- Deliver the CRM screens within the capstone schedule.
- Keep dependencies and production bundle size small.
- Support accessible, responsive interfaces using semantic HTML and clear focus states.
- Allow CRM-specific styling without fighting framework defaults.

## Considered Options

1. **Angular Material:** use Angular's component library and Material theming.
2. **Bootstrap:** use its CSS components and grid system, optionally with Angular wrappers.
3. **Plain CSS:** use semantic HTML, shared application styles and component-scoped CSS.

## Decision Outcome

Chosen option: **Plain CSS**, because the current application is a small CRM slice whose screens do not require a large prebuilt component set. It avoids adding and configuring a UI dependency, while keeping the visual design and markup straightforward for the team to adapt.

Use native semantic elements and Angular component styles for screen-specific presentation. Put genuinely shared foundations, such as typography and base styles, in the global stylesheet. Prefer accessible native controls and provide visible keyboard focus; plain CSS does not remove the application's responsibility to meet accessibility needs.

### Consequences

- Good, because no UI-library dependency or framework-specific setup is added.
- Good, because the team can tailor layouts and styles to the CRM workflow.
- Good, because native HTML semantics and controls remain directly available.
- Bad, because the team owns consistency, responsive behavior, accessibility details and reusable patterns.
- Bad, because complex controls such as date pickers or data tables may take more work than using a mature component library.

### Confirmation

- The frontend remains free of Angular Material and Bootstrap dependencies.
- Shared styles stay in `frontend/src/styles.css`; screen-specific styles stay with their Angular components.
- Review new screens for responsive layout, semantic controls, keyboard operation and visible focus.
- The existing frontend build remains the CI check for styling and application integration.

## More Information

- Revisit if: the UI grows to need complex, consistent controls across many screens, or the team cannot meet accessibility and responsive requirements efficiently with native controls and CSS.
- Confidence: medium
- Links: [ADR 0001: Freeze the taught stack](0001-freeze-the-taught-stack.md), [frontend package](../../frontend/package.json), [architecture](../architecture.md#stack)
