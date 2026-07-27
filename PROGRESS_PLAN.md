# Conductor OS Progress Save

Saved / updated on 2026-07-27.

## Objective

Build an AI-first mobile operating layer where:

- Voice chat is the primary interface
- Logged-in apps can be operated as user-granted agents
- Autonomy is user-configurable (including exact approval for posts/sends)
- Multi-app context (calendar, weather, Facebook/events, contacts, maps) feeds intent planning

See `FOUNDING.md` for locked product decisions and `ROADMAP.md` for phased exit criteria.

## Current product direction

Android-first **launcher + agent runtime** (not AOSP yet). Conductor owns:

- Voice intent handoff
- Cross-app context + personal graph grants
- Autonomy policy + approvals
- App-skill routing and accessibility-backed operation
- Durable timelines, receipts, handoffs

## Work completed in this save (honesty sprint)

### Founder / monorepo

- Locked founding decisions in `FOUNDING.md`
- Phased roadmap in `ROADMAP.md`
- Root `package.json` + `scripts/verify-all.mjs` for one-command verification

### Android correctness

- Added injectable `SystemClock` for wall-clock sessions, handoffs, freshness, and audit timestamps
- Production app-operation path uses `AccessibilityQueueingLiveBridge` (queues for AccessibilityService; **does not false-verify**)
- `RecordingAppOperationLiveBridge` explicitly labeled `recording_simulation` for tests only
- AccessibilityService now finalizes verified ops: consumes exact-approval receipts and autonomy budgets
- App-skills launcher surface closed: grouped by package with nested action rows
- Static Kotlin invariants updated for App skills + live-queue honesty + SystemClock
- Intent-aware scaffold voice responses that never claim external work completed

### Still true from prior progress

- Policy engine, playbooks, teach-app path, record store, mock outdoor connectors
- Voice capture/TTS boundaries, ephemeral token providers
- JS package suite for policy/graph/operator/orchestrator/evals

## Known incomplete / blockers

| Item | Status |
|------|--------|
| Real Gradle build / device install | Blocked by environment (no wrapper/SDK/ADB here) |
| Live OAuth/API connectors | Mock outdoor connectors only |
| Realtime model websocket | Token boundary real; stream still scaffolded |
| Room/SQLCipher production DB | Schema plan only; EncryptedSharedPreferences now |
| Arbitrary app reliability | Label-based playbooks + teach path; not hardened on device |
| Shared JS/Android contracts package | Still duplicated domain models |

## Next best steps (ordered)

1. On an Android SDK machine: add Gradle wrapper, `assembleDebug`, install, run `device_smoke_test.js --strict`
2. Wire one real connector (device Calendar free/busy or Google OAuth)
3. Replace canned realtime stream with provider websocket using ephemeral tokens
4. Harden Messages draft/send playbook against a real device UI tree
5. Extract shared action/policy contracts to stop JS/Android drift

## Verification

```bash
# From this folder
npm test

# Or Android static only
npm run test:android-static

# JS packages only
npm run test:js
```

Android device proof (when SDK available):

```bash
cd conductor-android-prototype
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
node device_smoke_test.js --strict
```

## Package map

- `conductor-android-prototype/` — product surface
- `conductor-runtime-core/` — monolithic outdoor workflow lab
- `conductor-action-sdk/` — action manifests + policy
- `conductor-app-operator/` — synthetic accessibility operator
- `conductor-connectors/` — purpose-scoped connector runtime
- `conductor-personal-graph/` — grants + redaction
- `conductor-voice-runtime/` — voice session state machine
- `conductor-os-orchestrator/` — JS end-to-end composition
- `conductor-evals/` — scenario suite
- `conductor-realtime-token-service/` — ephemeral token boundary
- `conductor-os-simulator/` — browser demo shell
