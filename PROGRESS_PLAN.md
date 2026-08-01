# Conductor OS Progress

Repo: https://github.com/aselsby/aifirstios  
Updated: 2026-08-01

## Goal

AI-first Android OS layer: voice, multi-app context, apps as subagents, autonomy + exact approval, honest live a11y ops.

## Gate status (self-improvement loop)

| Gate | Status | Evidence |
|------|--------|----------|
| G1 Build/install/smoke | **PASS** | `assembleDebug`, `device_smoke_test.js --strict` ready |
| G2 Outdoor multi-app plan | **PASS** | Live weather/places; Free Riverfront Park Walk |
| G3 Voice on hardware | **OPEN** | Needs physical phone STT path |
| G4 Live a11y verify | **PASS (controlled)** | See below |
| G5 Approval→execute | **OPEN** | Next loop |
| G6 Money safety | **PARTIAL** | Policy always requires exact approval; not full UI E2E |
| G7 Instant stop | **CODE** | Not re-proved this loop |
| G8 Real model stream | **OPEN** | Transport still scaffolded |

### G4 evidence (2026-08-01)

Controlled live demo surface `ConductorAgentDemoActivity` + playbook `conductor_demo_live_draft`.

Logcat:

```text
operator.live_verified operation_demo_app_draft:accessibility_live_tree:conductor_demo_live_draft:post_state_receipt
accessibility.queue_resolved operation_demo_app_draft:conductor_demo_live_draft
```

UI after verify:

- `Conductor demo signed in`
- body filled with demo utterance
- `demo draft ready`

Harness: `node device_live_g4_test.js`

**Note:** G4 is proven on a **deterministic in-app agent surface**, not yet on OEM Messages/Maps. That is intentional for reliability; OEM hardening remains P1.

## What this loop shipped

- Demo agent activity for live a11y proof
- Foreground assist from activity process (bypass bg activity limits)
- Rate-limited a11y relaunch (fix ANR storm)
- Root resolution via interactive windows
- Verifier improvements for set_text results
- `life_demo` intent path

## Remaining to full OS test-complete

1. G5: exact approval → resume queued send/post/purchase  
2. G4-OEM: Messages draft or Maps route live verify on real app UI  
3. G3: physical device voice  
4. G8: real LLM behind token service  
5. Money dry-run E2E without real funds  

## Verify

```bash
npm test
cd conductor-android-prototype
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
node device_smoke_test.js --strict
node device_live_g4_test.js
```
