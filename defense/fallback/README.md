# Fallback pack

What we show if the live demo breaks. The ladder for small failures (one UI step, Grafana, Kafka) is in Part 4 of the
[demo script](../demo-script.md). This folder is for when the stack or the laptop is gone and we're showing saved
evidence instead.

## When to switch

Switch when any of these happen:

- A beat has been stuck for 20 seconds and a restart won't fix it in another 20.
- The API, database or Docker is down on the demo laptop.
- The laptop, projector or network fails.

Then say this once and move on:

> "The live environment isn't cooperating, so we'll show the same steps from our rehearsal run. This shows the build
> worked earlier today; it doesn't prove it's working right now."

If the laptop is fine, play the recording. If the laptop is gone, open this folder on a phone or someone else's laptop
from GitHub.

## Beat by beat

| Demo beat | Open | What it proves | What it doesn't |
| --- | --- | --- | --- |
| Sign in, search, profile | [Sign in](local-run-2026-10-12.md#sign-in), [Search and profile](local-run-2026-10-12.md#search-and-profile) | real login, search by name, the timeline comes from the API | the screens themselves; use the recording |
| Record a call, row, consumer, dashboard | [Save, row, event, consumer](local-run-2026-10-12.md#save-row-event-consumer) | one correlation ID in the row, the event, the consumer log; processed +1, lag 0 | that today's build does it |
| Edit conflict | [Edit conflict](local-run-2026-10-12.md#edit-conflict) | tab 2 saves (version 1), tab 1's stale save is a 409 | the UI's conflict message; that's in the recording |
| Ravi's status, create, delete | [Ravi](local-run-2026-10-12.md#ravi-prospect-to-active-then-back), [Admin](local-run-2026-10-12.md#admin-creates-a-customer-and-deletes-a-call) | PROSPECT to ACTIVE works, back is a 409 and Ravi stays ACTIVE; admin creates `CUS-10001` and deletes a call, an agent can't create | |
| 401 / 403 | [Deny](local-run-2026-10-12.md#deny) | the API refuses no token and refuses an agent | |
| Broken consumer, DLT, replay twice | [Break the consumer](local-run-2026-10-12.md#break-the-consumer), [Fix and replay twice](local-run-2026-10-12.md#fix-and-replay-twice) | version 999 goes to the DLT with no retries and the reason in its headers; replay handled once, skipped the second time; counters before and after | the Grafana chart itself; the counters are the same numbers |
| Release | [v1.1.0 CD run](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38308570206), below | deployed by digest, refused four times first, smoke 13/13 | |
| Rollback, if asked | [`reports/release-v0.1.1-2026-10-08.md`](../../reports/release-v0.1.1-2026-10-08.md) | live rollback in 1 min 24 s, smoke 12/12 after | a rollback of `v1.1.0` |

## Release evidence for v1.1.0

From the CD run's logs. The GitHub page works from any browser, so this beat survives a dead laptop.

- `main` CI for `9bf13d2`: [run 38308415270](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38308415270), success.
- CD [run 38308570206](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38308570206), five attempts:

  | Attempt | Started (UTC) | Result | Stopped at |
  | --- | --- | --- | --- |
  | 1 | 21:06 | refused | "No successful main CI run for 9bf13d2… Tag a commit that main has built." |
  | 2 | 21:11 | refused | "The namespace doesn't match infra/terraform. Run CD with infra-plan, review it, then infra-apply." |
  | 3, 4 | 21:14, 21:16 | refused | same drift check |
  | 5 | 21:22 | deployed | smoke 13/13 |

  Between 4 and 5: [infra-plan](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38309282234)
  at 21:13, [infra-apply](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38309982398)
  at 21:20.

- Backup before the deploy: `crm-20261012T212339Z.dump`.
- Deployed:

  ```text
  crm-api@sha256:402405ae3f253aff714fc7a258bb7a2889d0f2306d18a1a4d8174a30b77879e1
  crm-ui@sha256:e5f35588bd251a5ce0b301c44236918f40e5c96d17c1ed053baa2b13ed457662
  ```

- Smoke, attempt 5:

  ```text
  PASS readiness over trusted TLS
  PASS UI served through the Ingress
  PASS HTTP redirects to HTTPS
  PASS anonymous API call is 401
  PASS wrong password is 401
  PASS real login for agent1 and admin1
  PASS AGENT cannot reach Actuator through Ingress
  PASS ADMIN cannot reach Actuator through Ingress
  PASS Prometheus is not reachable through Ingress
  PASS read CUS-1001
  PASS read CUS-1002
  PASS record an interaction with lab-request-001
  PASS unknown customer is 404
  ```

The `v1.1.0` release notes still have the template's `...` placeholders for digests and smoke; use the values above.

## The recording

Record one full rehearsal on the demo laptop, screen and audio, and keep it on the laptop and a USB stick. It doesn't
go in Git because it's too big. Watch it through once before the panel and cut anything that shows a token or the
production address.

## Before the panel

- [ ] Recording on the laptop and the USB stick, and it plays
- [ ] This folder and the CD run open in a browser tab
- [ ] The repo open on a second device, in case the demo laptop dies
