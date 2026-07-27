# Conductor OS

AI-first Android operating layer: **voice in, multi-app context, policy-gated action, apps as agents.**

## Start here

1. [FOUNDING.md](FOUNDING.md) — product bet and non-negotiables  
2. [ROADMAP.md](ROADMAP.md) — phases and exit criteria  
3. [PROGRESS_PLAN.md](PROGRESS_PLAN.md) — current checkpoint status  
4. [conductor-android-prototype/README.md](conductor-android-prototype/README.md) — native surface  

## Hero flow

User: *“What should I do outside this afternoon and draft an invite to Maya?”*

Conductor:

1. Classifies outdoor intent from voice/text  
2. Gathers purpose-scoped calendar, weather, events, contacts, maps  
3. Plans ranked option + draft invite + optional maps/calendar hold  
4. Enforces autonomy (draft free; send/post exact-approval)  
5. Queues live app work for AccessibilityService when the target app is open  
6. Audits everything  

## Verify

```bash
npm test
```

Requires Node ≥ 20. Native install/device smoke remains environment-gated until Android SDK + Gradle wrapper are present.

## Architecture (short)

```
Voice → Intent → Context/Graph → Planner → Policy/Approvals
  → Tools (app operator | intents | connectors) → Audit
```

The model never gets direct authority to act.
