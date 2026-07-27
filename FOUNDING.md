# Conductor OS — Founding Decisions

Working name: **Conductor OS**  
Date: 2026-07-27  
Mode: one-person technical founder shop

## Company bet

Apps become tools. The user delegates outcomes. Conductor is the trusted mobile operating layer between **intent**, **cross-app context**, and **action**.

## Product definition

Conductor is an **AI-first Android launcher + agent runtime**, not a chat widget bolted onto an app grid.

Primary interface: **voice chat** (text fallback).  
Primary unit of work: a **task**, not an app icon.  
Primary capability: once the user is logged into an app and grants Conductor, that app can be operated as an **agent surface** under user-controlled autonomy.

## Non-negotiable product rules

1. **Voice first** — speak a goal; interrupt anytime; see transcript and plan.
2. **Apps as agents** — after login + grant, playbooks (built-in or taught) operate the app on the user's behalf.
3. **Configurable autonomy** — Ask Only, Draft Only, Low-Risk Auto, Trusted Auto; per-app overrides; instant stop to Ask Only.
4. **Exact approval for high-impact side effects** — public posts, outbound messages, spend, destructive actions.
5. **Multi-app context with provenance** — calendar, weather, events, contacts, maps, preferences; purpose-scoped grants; no silent re-grant after revoke.
6. **Never treat the model as authority to act** — plan → policy → approval → typed execution → verification → audit.
7. **Live app operation must be honest** — recording/simulation cannot masquerade as verified accessibility execution in production.
8. **Android first** — full agent OS on Android; iOS is a companion later, not the v1 OS claim.

## MVP promise (what “real” means for v0)

> The user says: “What should I do outside this afternoon and draft an invite to Maya?”  
> Conductor gathers calendar, weather, and nearby events; ranks an option; drafts the invite; **asks before sending**; can open maps / hold calendar under policy; leaves a durable audit trail.

Success is not “the model answers.” Success is **context + plan + controlled action**.

## Architecture shape (locked)

```
Voice / Launcher
    → Intent router
    → Context broker + personal graph (purpose grants)
    → Planner
    → Policy engine + approvals
    → Tool registry
         → App-operation executor (sessions, budgets, receipts)
              → Accessibility live bridge (only real UI operator)
         → Android intents / connectors
    → Audit ledger + operation timelines
```

## Platform decision

| Choice | Decision | Why |
|--------|----------|-----|
| First surface | Android launcher app | HOME entry, AccessibilityService, intents, permissions |
| Not first | AOSP fork | Too slow for product learning |
| App operation | Accessibility + intents + APIs | Arbitrary apps need a11y; APIs when available |
| Storage | Encrypted local record store → Room/SQLCipher | Local-first trust |
| Model access | Backend ephemeral tokens only | No provider secrets on device |

## Execution priorities (founder order)

1. **Honesty & safety** — clocks, live vs simulated execution, approvals, audit.
2. **Hero workflow** — outdoor plan + draft + approval + send handoff path.
3. **App-skill management** — apps as grouped agent skills, not flat playbook soup.
4. **Device proof** — Gradle, install, a11y, HOME intent smoke.
5. **Real connectors** — calendar, weather, events with real credentials.
6. **Realtime voice model** — replace canned transport with true session streaming.
7. **Teach-any-app reliability** — selectors, recovery, multi-field playbooks at scale.

## What this checkpoint is

A **control-plane prototype** with a strong safety model and an Android product surface.  
It is not yet a production OS image. Every claim of “works” must specify: **in simulation**, **on device**, or **with live connectors**.
